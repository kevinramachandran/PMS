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

    public GembaWalkConfigService(GembaWalkRecordRepository repository,
                                  GembaWalkMasterDataService masterDataService,
                                  PlantMasterDataService plantMasterDataService,
                                  AppUserRepository userRepository,
                                  EmailConfigService emailConfigService) {
        this.repository = repository;
        this.masterDataService = masterDataService;
        this.plantMasterDataService = plantMasterDataService;
        this.userRepository = userRepository;
        this.emailConfigService = emailConfigService;
    }

    public Optional<GembaWalkRecord> find(Long id) {
        return repository.findById(id);
    }

    public List<GembaWalkRecord> list() {
        return repository.findAll();
    }

    @Transactional
    public GembaWalkRecord create(GembaWalkRecord record, String username) {
        applyDefaults(record, username);
        replaceObservations(record, record.getObservations());
        GembaWalkRecord saved = repository.save(record);
        notifyAreaHod(saved, "Gemba Walk Observation Submitted: " + label(saved), submittedBody(saved));
        return saved;
    }

    @Transactional
    public Optional<GembaWalkRecord> update(Long id, GembaWalkRecord incoming, String username) {
        return repository.findById(id).map(existing -> {
            boolean hadOpenObservation = hasOpenObservation(existing);
            existing.setScheduleItemId(incoming.getScheduleItemId());
            existing.setStartTime(trim(incoming.getStartTime()));
            existing.setCompletionTime(trim(incoming.getCompletionTime()));
            existing.setEmail(trim(incoming.getEmail()));
            existing.setManagerName(trim(incoming.getManagerName()));
            existing.setDateOfLeadershipSafetyWalkConducted(incoming.getDateOfLeadershipSafetyWalkConducted());
            existing.setManagementSafetyWalkWeek(trim(incoming.getManagementSafetyWalkWeek()));
            existing.setLocationOfMswConducted(trim(incoming.getLocationOfMswConducted()));
            existing.setResponsibility(trim(incoming.getResponsibility()));
            applyDefaults(existing, username);
            replaceObservations(existing, incoming.getObservations());
            GembaWalkRecord saved = repository.save(existing);
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
        options.put("processAreas", plantMasterDataService.names(PlantMasterDataService.PROCESS_AREA));
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

    private void applyDefaults(GembaWalkRecord record, String username) {
        currentUser(username).ifPresent(user -> {
            if (isBlank(record.getEmail())) {
                record.setEmail(trim(user.getEmail()));
            }
            if (isBlank(record.getManagerName())) {
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
        validateConfigured(record.getLocationOfMswConducted(), plantMasterDataService.names(PlantMasterDataService.PROCESS_AREA), "Location of MSW Conducted");
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

    private void notifyClosed(GembaWalkRecord record) {
        List<String> recipients = new ArrayList<>(areaHodEmails(record.getLocationOfMswConducted()));
        if (!isBlank(record.getEmail()) && !recipients.contains(record.getEmail())) {
            recipients.add(record.getEmail());
        }
        emailConfigService.sendEmail(recipients, "Gemba Walk Observation Closed: " + label(record), closedBody(record));
    }

    private void notifyAreaHod(GembaWalkRecord record, String subject, String body) {
        emailConfigService.sendEmail(areaHodEmails(record.getLocationOfMswConducted()), subject, body);
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
                .filter(user -> isHod(user) || isAssignable(user))
                .toList();
        List<AppUser> users = scoped.isEmpty()
                ? activeUsers().stream().filter(user -> isHod(user) || isAssignable(user)).toList()
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

    private boolean isAssignable(AppUser user) {
        String designation = trim(user.getDesignation()).toUpperCase(Locale.ENGLISH);
        return RoleAccess.isAssignableOperationalRole(user.getRole())
                || designation.equals("ENGINEER")
                || designation.equals("EXECUTIVE")
                || designation.equals("OPERATOR");
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
        return "Gemba Walk observation " + label(record) + " has been submitted for " + trim(record.getLocationOfMswConducted()) + ".";
    }

    private String closedBody(GembaWalkRecord record) {
        return "Gemba Walk observation " + label(record) + " has been closed.";
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
