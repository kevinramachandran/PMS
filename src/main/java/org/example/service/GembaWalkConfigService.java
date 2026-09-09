package org.example.service;

import org.example.entity.AppUser;
import org.example.entity.GembaWalkObservation;
import org.example.entity.GembaWalkRecord;
import org.example.repository.AppUserRepository;
import org.example.repository.GembaWalkRecordRepository;
import org.example.util.RoleAccess;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class GembaWalkConfigService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final GembaWalkRecordRepository repository;
    private final GembaWalkMasterDataService masterDataService;
    private final PlantMasterDataService plantMasterDataService;
    private final AppUserRepository userRepository;
    private final EmailConfigService emailConfigService;
    private final AssignmentHistoryService assignmentHistoryService;

    public GembaWalkConfigService(GembaWalkRecordRepository repository,
                                  GembaWalkMasterDataService masterDataService,
                                  PlantMasterDataService plantMasterDataService,
                                  AppUserRepository userRepository,
                                  EmailConfigService emailConfigService,
                                  AssignmentHistoryService assignmentHistoryService) {
        this.repository = repository;
        this.masterDataService = masterDataService;
        this.plantMasterDataService = plantMasterDataService;
        this.userRepository = userRepository;
        this.emailConfigService = emailConfigService;
        this.assignmentHistoryService = assignmentHistoryService;
    }

    public Optional<GembaWalkRecord> find(Long id) {
        return repository.findById(id);
    }

    public List<GembaWalkRecord> list() {
        return repository.findAll();
    }

    @Transactional
    public GembaWalkRecord create(GembaWalkRecord record, String username) {
        applyDefaults(record, username, true);
        replaceObservations(record, record.getObservations());
        GembaWalkRecord saved = repository.save(record);
        assignmentHistoryService.record("gemba-walk", saved.getId(), "", saved.getResponsibility(), saved.getAssignmentRemark(), username, saved.getLocationOfMswConducted());
        notifyAreaHod(saved, "Gemba Walk Observation Submitted: " + label(saved), submittedBody(saved));
        return saved;
    }

    @Transactional
    public Optional<GembaWalkRecord> update(Long id, GembaWalkRecord incoming, String username) {
        return repository.findById(id).map(existing -> {
            String previousAssignee = existing.getResponsibility();
            boolean hadOpenObservation = hasOpenObservation(existing);
            existing.setResponsibility(trim(incoming.getResponsibility()));
            existing.setAssignmentRemark(trim(incoming.getAssignmentRemark()));
            existing.setFinalComments(trim(incoming.getFinalComments()));
            applyDefaults(existing, username, false);
            updateEditableObservationFields(existing, incoming.getObservations());
            GembaWalkRecord saved = repository.save(existing);
            assignmentHistoryService.record("gemba-walk", saved.getId(), previousAssignee, saved.getResponsibility(), incoming.getAssignmentRemark(), username, saved.getLocationOfMswConducted());
            if (hadOpenObservation && !hasOpenObservation(saved)) {
                notifyClosed(saved);
            }
            return saved;
        });
    }

    public Map<String, Object> options(String username, String location) {
        Map<String, Object> options = new LinkedHashMap<>();
        Optional<AppUser> currentUser = currentUser(username);
        options.put("currentUser", currentUser.map(this::userOption).orElse(Map.of()));
        options.put("gembaCategories", masterDataService.names(GembaWalkMasterDataService.GEMBA_CATEGORY));
        options.put("lifeSaverRules", masterDataService.names(GembaWalkMasterDataService.LIFE_SAVER_RULE));
        options.put("plants", plantMasterDataService.names(PlantMasterDataService.PLANT));
        options.put("departments", plantMasterDataService.names(PlantMasterDataService.DEPARTMENT));
        options.put("processAreas", plantMasterDataService.names(PlantMasterDataService.PROCESS_AREA));
        options.put("plantItems", plantMasterDataService.list(PlantMasterDataService.PLANT));
        options.put("departmentItems", plantMasterDataService.list(PlantMasterDataService.DEPARTMENT));
        options.put("areaItems", plantMasterDataService.list(PlantMasterDataService.PROCESS_AREA));
        options.put("responsibilityUsers", responsibilityUsers(location));
        options.put("defaultResponsibility", defaultResponsibility(location).orElse(""));
        return options;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "${app.timezone:Asia/Calcutta}")
    public void sendDailyDashboardReport() {
        if (!emailConfigService.isGembaWalkDailyEnabled()) {
            return;
        }
        List<GembaWalkRecord> rows = list();
        int closed = (int) rows.stream().filter(row -> !hasOpenObservation(row)).count();
        emailConfigService.sendEmail(
                emailConfigService.configuredReportRecipients(),
                "Gemba Walk Daily Report",
                buildDailyReportBody(rows, rows.size(), closed),
                true,
                true
        );
    }

    private void applyDefaults(GembaWalkRecord record, String username, boolean forceUserIdentity) {
        currentUser(username).ifPresent(user -> {
            if (forceUserIdentity || isBlank(record.getEmail())) {
                record.setEmail(trim(user.getEmail()));
            }
            if (forceUserIdentity || isBlank(record.getManagerName())) {
                record.setManagerName(firstNonBlank(user.getName(), user.getUsername()));
            }
        });
        if (isBlank(record.getStartTime())) {
            record.setStartTime(LocalTime.now().format(TIME_FORMATTER));
        }
        if (isBlank(record.getCompletionTime())) {
            record.setCompletionTime(LocalTime.now().format(TIME_FORMATTER));
        }
        if (record.getDateOfLeadershipSafetyWalkConducted() == null) {
            record.setDateOfLeadershipSafetyWalkConducted(LocalDate.now());
        }
        if (isBlank(record.getResponsibility())) {
            defaultResponsibility(record.getLocationOfMswConducted()).ifPresent(record::setResponsibility);
        }
        record.setFinalComments(trim(record.getFinalComments()));
        validateConfigured(record.getLocationOfMswConducted(), plantMasterDataService.names(PlantMasterDataService.PROCESS_AREA), "Location of MSW Conducted");
        validateResponsibility(record.getResponsibility(), record.getLocationOfMswConducted());
    }

    private void replaceObservations(GembaWalkRecord record, List<GembaWalkObservation> observations) {
        if (record.getObservations() == null) {
            record.setObservations(new ArrayList<>());
        }
        record.getObservations().clear();
        List<GembaWalkObservation> source = observations == null ? List.of() : new ArrayList<>(observations);
        int order = 1;
        for (GembaWalkObservation observation : source) {
            GembaWalkObservation item = new GembaWalkObservation();
            item.setObservationOrder(order++);
            item.setObservationDescription(trim(observation.getObservationDescription()));
            item.setPictureImage(trim(observation.getPictureImage()));
            validateConfigured(observation.getGembaCategory(), masterDataService.names(GembaWalkMasterDataService.GEMBA_CATEGORY), "Gemba Category");
            validateConfigured(observation.getLifeSaverRule(), masterDataService.names(GembaWalkMasterDataService.LIFE_SAVER_RULE), "Life Saver Rule (LSR)");
            item.setGembaCategory(trim(observation.getGembaCategory()));
            item.setLifeSaverRule(trim(observation.getLifeSaverRule()));
            item.setStatus(normalizeStatus(observation.getStatus()));
            item.setRecord(record);
            record.getObservations().add(item);
        }
    }

    private void updateEditableObservationFields(GembaWalkRecord record, List<GembaWalkObservation> observations) {
        if (record.getObservations() == null) {
            record.setObservations(new ArrayList<>());
        }
        List<GembaWalkObservation> incoming = observations == null ? List.of() : observations;
        for (int index = 0; index < record.getObservations().size(); index++) {
            if (index >= incoming.size()) {
                break;
            }
            GembaWalkObservation target = record.getObservations().get(index);
            GembaWalkObservation source = incoming.get(index);
            target.setPictureImage(trim(source.getPictureImage()));
            target.setStatus(normalizeStatus(source.getStatus()));
        }
    }

    private boolean hasOpenObservation(GembaWalkRecord record) {
        return record.getObservations().stream()
                .anyMatch(observation -> !"Closed".equalsIgnoreCase(trim(observation.getStatus())));
    }

    private void validateConfigured(String value, List<String> options, String label) {
        String trimmed = trim(value);
        if (trimmed.isBlank()) {
            return;
        }
        boolean configured = options.stream()
                .anyMatch(option -> option != null && option.trim().equalsIgnoreCase(trimmed));
        if (!configured) {
            throw new IllegalArgumentException(label + " must be configured in Master Data");
        }
    }

    private void validateResponsibility(String value, String location) {
        String trimmed = trim(value);
        if (trimmed.isBlank()) {
            return;
        }
        boolean configured = responsibilityUsers(location).stream()
                .anyMatch(user -> trim(user.get("username")).equalsIgnoreCase(trimmed)
                        || trim(user.get("label")).equalsIgnoreCase(trimmed));
        if (!configured) {
            throw new IllegalArgumentException("Responsibility must be assigned to an HoD");
        }
    }

    private void notifyClosed(GembaWalkRecord record) {
        List<String> recipients = new ArrayList<>(areaHodEmails(record.getLocationOfMswConducted()));
        if (!isBlank(record.getEmail()) && !recipients.contains(record.getEmail())) {
            recipients.add(record.getEmail());
        }
        responsibilityEmail(record.getResponsibility()).ifPresent(email -> {
            if (!recipients.contains(email)) {
                recipients.add(email);
            }
        });
        emailConfigService.sendEmail(recipients, "Gemba Walk Observation Closed: " + label(record), closedBody(record), true, true);
    }

    private void notifyAreaHod(GembaWalkRecord record, String subject, String body) {
        emailConfigService.sendEmail(gembaWalkRecipients(record), subject, body, true, true);
    }

    private List<String> gembaWalkRecipients(GembaWalkRecord record) {
        List<String> recipients = new ArrayList<>(areaHodEmails(record.getLocationOfMswConducted()));
        responsibilityEmail(record.getResponsibility()).ifPresent(email -> {
            if (!recipients.contains(email)) {
                recipients.add(email);
            }
        });
        return recipients;
    }

    private Optional<String> responsibilityEmail(String responsibility) {
        String value = trim(responsibility);
        if (value.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByUsernameIgnoreCase(value)
                .or(() -> userRepository.findByEmailIgnoreCase(value))
                .or(() -> userRepository.findByNameIgnoreCase(value))
                .or(() -> userRepository.findByEmployeeIdIgnoreCase(value))
                .map(AppUser::getEmail)
                .filter(email -> !isBlank(email));
    }

    private List<String> areaHodEmails(String location) {
        return activeUsers().stream()
                .filter(user -> isAreaMatch(user, location))
                .filter(this::isHod)
                .map(AppUser::getEmail)
                .filter(email -> !isBlank(email))
                .distinct()
                .toList();
    }

    private Optional<String> defaultResponsibility(String location) {
        return activeUsers().stream()
                .filter(user -> isAreaMatch(user, location))
                .filter(this::isHod)
                .map(user -> firstNonBlank(user.getUsername(), user.getName()))
                .findFirst();
    }

    private List<Map<String, String>> responsibilityUsers(String location) {
        List<AppUser> scoped = activeUsers().stream()
                .filter(user -> isAreaMatch(user, location))
                .filter(this::isHod)
                .toList();
        List<AppUser> users = scoped.isEmpty()
                ? activeUsers().stream().filter(this::isHod).toList()
                : scoped;
        return users.stream().map(this::userOption).toList();
    }

    private List<AppUser> activeUsers() {
        return userRepository.findAll().stream()
                .filter(user -> "ACTIVE".equalsIgnoreCase(trim(user.getStatus())))
                .toList();
    }

    private Optional<AppUser> currentUser(String username) {
        if (isBlank(username)) {
            return Optional.empty();
        }
        return userRepository.findByUsernameIgnoreCase(username);
    }

    private boolean isAreaMatch(AppUser user, String location) {
        if (isBlank(location)) {
            return true;
        }
        String normalizedLocation = location.trim().toLowerCase(Locale.ENGLISH);
        return trim(user.getArea()).toLowerCase(Locale.ENGLISH).equals(normalizedLocation)
                || trim(user.getDepartment()).toLowerCase(Locale.ENGLISH).equals(normalizedLocation);
    }

    private boolean isHod(AppUser user) {
        String designation = trim(user.getDesignation()).toUpperCase(Locale.ENGLISH);
        return RoleAccess.isHod(user.getRole())
                || designation.contains("HOD")
                || designation.contains("HEAD_OF_DEPARTMENT");
    }

    private Map<String, String> userOption(AppUser user) {
        String username = firstNonBlank(user.getUsername(), user.getEmail());
        String label = firstNonBlank(user.getName(), user.getUsername());
        return Map.of(
                "username", username,
                "label", label,
                "email", trim(user.getEmail())
        );
    }

    private String normalizeStatus(String status) {
        return "Closed".equalsIgnoreCase(trim(status)) ? "Closed" : "Open";
    }

    private String label(GembaWalkRecord record) {
        return "#" + record.getId();
    }

    private String submittedBody(GembaWalkRecord record) {
        return buildGembaWalkEmailBody(
                "Gemba Walk Observation Submitted",
                "A Gemba Walk observation has been submitted for review.",
                record
        );
    }

    private String closedBody(GembaWalkRecord record) {
        return buildGembaWalkEmailBody(
                "Gemba Walk Observation Closed",
                "All observations in this Gemba Walk have been closed.",
                record
        );
    }

    private String buildGembaWalkEmailBody(String title, String intro, GembaWalkRecord record) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='margin:0;padding:0;background:#f5f7f9;font-family:Arial,sans-serif;color:#1f2937;'>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='100%' style='background:#f5f7f9;padding:24px 0;'>")
                .append("<tr><td align='center'>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='680' style='max-width:680px;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;'>")
                .append("<tr><td style='background:#003d24;padding:16px 20px;'>")
                .append("<img src='cid:brandLogo' alt='Carlsberg logo' style='height:34px;width:auto;display:block;'>")
                .append("</td></tr><tr><td style='padding:20px;'>")
                .append("<h2 style='margin:0 0 8px 0;color:#003d24;font-size:20px;'>")
                .append(escapeHtml(title))
                .append("</h2>")
                .append("<p style='margin:0 0 16px;font-size:14px;line-height:1.5;'>")
                .append(escapeHtml(intro))
                .append("</p>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='100%' style='border-collapse:collapse;font-size:14px;'>")
                .append(detailRow("Record ID", label(record)))
                .append(detailRow("Manager", record.getManagerName()))
                .append(detailRow("Email", record.getEmail()))
                .append(detailRow("Date Conducted", record.getDateOfLeadershipSafetyWalkConducted() == null ? "" : DATE_FORMATTER.format(record.getDateOfLeadershipSafetyWalkConducted())))
                .append(detailRow("Week", record.getManagementSafetyWalkWeek()))
                .append(detailRow("Location", record.getLocationOfMswConducted()))
                .append(detailRow("Responsibility", record.getResponsibility()))
                .append(detailRow("Start Time", record.getStartTime()))
                .append(detailRow("Completion Time", record.getCompletionTime()));
        if (!isBlank(record.getFinalComments())) {
            html.append(detailRow("Final Comments", record.getFinalComments()));
        }
        html.append("</table>");

        if (record.getObservations() != null && !record.getObservations().isEmpty()) {
            html.append("<h3 style='margin:18px 0 8px;color:#003d24;font-size:16px;'>Observations</h3>")
                    .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='100%' style='border-collapse:collapse;font-size:13px;'>")
                    .append("<tr>")
                    .append(headerCell("No."))
                    .append(headerCell("Observation"))
                    .append(headerCell("Category"))
                    .append(headerCell("LSR"))
                    .append(headerCell("Status"))
                    .append("</tr>");
            for (GembaWalkObservation observation : record.getObservations()) {
                html.append("<tr>")
                        .append(bodyCell(observation.getObservationOrder() == null ? "" : String.valueOf(observation.getObservationOrder())))
                        .append(bodyCell(observation.getObservationDescription()))
                        .append(bodyCell(observation.getGembaCategory()))
                        .append(bodyCell(observation.getLifeSaverRule()))
                        .append(bodyCell(observation.getStatus()))
                        .append("</tr>");
            }
            html.append("</table>");
        }

        html.append("<p style='margin:16px 0 0 0;font-size:13px;color:#6b7280;'>Regards,<br>Brewery PMS</p>")
                .append("</td></tr></table></td></tr></table></body></html>");
        return html.toString();
    }

    private String detailRow(String label, String value) {
        return "<tr><td style='padding:8px 10px;background:#f9fafb;border:1px solid #e5e7eb;width:190px;color:#374151;font-weight:600;'>"
                + escapeHtml(label)
                + "</td><td style='padding:8px 10px;border:1px solid #e5e7eb;color:#111827;'>"
                + escapeHtml(isBlank(value) ? "-" : value)
                + "</td></tr>";
    }

    private String buildDailyReportBody(List<GembaWalkRecord> rows, int reported, int closed) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='margin:0;padding:0;background:#f5f7f9;font-family:Arial,sans-serif;color:#1f2937;'>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='100%' style='background:#f5f7f9;padding:24px 0;'>")
                .append("<tr><td align='center'>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='760' style='max-width:760px;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;'>")
                .append("<tr><td style='background:#003d24;padding:16px 20px;'>")
                .append("<img src='cid:brandLogo' alt='Carlsberg logo' style='height:34px;width:auto;display:block;'>")
                .append("</td></tr><tr><td style='padding:20px;'>")
                .append("<h2 style='margin:0 0 12px 0;color:#003d24;font-size:20px;'>Gemba Walk Daily Report</h2>")
                .append("<p style='margin:0 0 16px;font-size:14px;'>Reported: ")
                .append(reported)
                .append(" | Closed: ")
                .append(closed)
                .append("</p>")
                .append("<table cellspacing='0' cellpadding='0' border='0' width='100%' style='border-collapse:collapse;font-size:13px;'>")
                .append("<tr>")
                .append(headerCell("ID / Serial Number"))
                .append(headerCell("Date of Leadership Safety Walk Conducted"))
                .append(headerCell("Management Safety Walk Week"))
                .append(headerCell("Location of MSW Conducted"))
                .append(headerCell("Responsibility"))
                .append(headerCell("Observation Status"))
                .append("</tr>");
        for (GembaWalkRecord row : rows) {
            html.append("<tr>")
                    .append(bodyCell(row.getId() == null ? "" : String.valueOf(row.getId())))
                    .append(bodyCell(row.getDateOfLeadershipSafetyWalkConducted() == null ? "" : DATE_FORMATTER.format(row.getDateOfLeadershipSafetyWalkConducted())))
                    .append(bodyCell(row.getManagementSafetyWalkWeek()))
                    .append(bodyCell(row.getLocationOfMswConducted()))
                    .append(bodyCell(row.getResponsibility()))
                    .append(bodyCell(hasOpenObservation(row) ? "Open" : "Closed"))
                    .append("</tr>");
        }
        html.append("</table></td></tr></table></td></tr></table></body></html>");
        return html.toString();
    }

    private String headerCell(String value) {
        return "<th style='padding:8px;border:1px solid #e5e7eb;background:#f9fafb;color:#374151;text-align:left;'>"
                + escapeHtml(value)
                + "</th>";
    }

    private String bodyCell(String value) {
        return "<td style='padding:8px;border:1px solid #e5e7eb;color:#111827;'>"
                + escapeHtml(isBlank(value) ? "-" : value)
                + "</td>";
    }

    private String escapeHtml(String value) {
        return trim(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? trim(second) : trim(first);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
