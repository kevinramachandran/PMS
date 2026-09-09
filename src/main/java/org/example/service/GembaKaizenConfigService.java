package org.example.service;

import org.example.entity.AppUser;
import org.example.entity.GembaKaizenRecord;
import org.example.repository.AppUserRepository;
import org.example.repository.GembaKaizenRecordRepository;
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
public class GembaKaizenConfigService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final GembaKaizenRecordRepository repository;
    private final GembaKaizenMasterDataService kaizenMasterDataService;
    private final PlantMasterDataService plantMasterDataService;
    private final AppUserRepository userRepository;
    private final EmailConfigService emailConfigService;
    private final AssignmentHistoryService assignmentHistoryService;

    public GembaKaizenConfigService(GembaKaizenRecordRepository repository,
                                    GembaKaizenMasterDataService kaizenMasterDataService,
                                    PlantMasterDataService plantMasterDataService,
                                    AppUserRepository userRepository,
                                    EmailConfigService emailConfigService,
                                    AssignmentHistoryService assignmentHistoryService) {
        this.repository = repository;
        this.kaizenMasterDataService = kaizenMasterDataService;
        this.plantMasterDataService = plantMasterDataService;
        this.userRepository = userRepository;
        this.emailConfigService = emailConfigService;
        this.assignmentHistoryService = assignmentHistoryService;
    }

    public List<GembaKaizenRecord> list() {
        return repository.findAllByOrderByGembaKaizenGenerationDateDescIdDesc();
    }

    public Optional<GembaKaizenRecord> find(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public GembaKaizenRecord create(GembaKaizenRecord record, String username) {
        applyDefaults(record, username);
        GembaKaizenRecord saved = repository.save(record);
        assignmentHistoryService.record("gemba-kaizen", saved.getId(), "", saved.getAssignedTo(), saved.getAssignmentRemark(), username, saved.getGembaKaizenLocation());
        notifyHods(saved, username, "Gemba Kaizen Submitted: #" + saved.getId(), buildKaizenEmailBody("Gemba Kaizen Submitted", "A Gemba Kaizen idea has been submitted for review.", saved));
        if (isImplemented(saved)) {
            notifyClosed(saved, username);
        }
        return saved;
    }

    @Transactional
    public Optional<GembaKaizenRecord> update(Long id, GembaKaizenRecord incoming, String username) {
        return repository.findById(id).map(existing -> {
            String previousAssignee = existing.getAssignedTo();
            boolean wasImplemented = isImplemented(existing);
            existing.setPictureImage(trim(incoming.getPictureImage()));
            existing.setIsKaizenImplemented(normalizeYesNo(incoming.getIsKaizenImplemented()));
            existing.setAssignedTo(trim(incoming.getAssignedTo()));
            existing.setAssignmentRemark(incoming.getAssignmentRemark());
            applyDefaults(existing, username);
            GembaKaizenRecord saved = repository.save(existing);
            assignmentHistoryService.record("gemba-kaizen", saved.getId(), previousAssignee, saved.getAssignedTo(), incoming.getAssignmentRemark(), username, saved.getGembaKaizenLocation());
            if (!wasImplemented && isImplemented(saved)) {
                notifyClosed(saved, username);
            }
            return saved;
        });
    }

    public Map<String, Object> options(String username) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("currentUser", currentUser(username).map(this::userOption).orElse(Map.of()));
        options.put("departments", plantMasterDataService.names(PlantMasterDataService.DEPARTMENT));
        options.put("processAreas", plantMasterDataService.names(PlantMasterDataService.PROCESS_AREA));
        options.put("classifications", kaizenMasterDataService.names(GembaKaizenMasterDataService.CLASSIFICATION_OF_KAIZEN));
        options.put("assignmentUsers", activeUsers().stream().filter(user -> isHod(user) || isAssignable(user)).map(this::userOption).toList());
        return options;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "${app.timezone:Asia/Calcutta}")
    public void sendDailyDashboardReport() {
        if (!emailConfigService.isGembaKaizenDailyEnabled()) {
            return;
        }
        List<GembaKaizenRecord> rows = list();
        int reported = rows.size();
        int closed = (int) rows.stream().filter(this::isImplemented).count();
        emailConfigService.sendEmail(
                emailConfigService.configuredReportRecipients(),
                "Gemba Kaizen Daily Report",
                buildDailyReportBody(rows, reported, closed),
                true,
                true
        );
    }

    private void applyDefaults(GembaKaizenRecord record, String username) {
        currentUser(username).ifPresent(user -> {
            if (isBlank(record.getName())) {
                record.setName(firstNonBlank(user.getName(), user.getUsername()));
            }
            if (isBlank(record.getEmployeeIdHoNumber())) {
                record.setEmployeeIdHoNumber(trim(user.getEmployeeId()));
            }
        });
        if (isBlank(record.getLastModifiedTime())) {
            record.setLastModifiedTime(LocalTime.now().format(TIME_FORMATTER));
        }
        if (record.getGembaKaizenGenerationDate() == null) {
            record.setGembaKaizenGenerationDate(LocalDate.now());
        }
        record.setIsKaizenImplemented(normalizeYesNo(record.getIsKaizenImplemented()));
        validateConfigured(record.getDepartment(), plantMasterDataService.names(PlantMasterDataService.DEPARTMENT), "Department");
        validateConfigured(record.getGembaKaizenLocation(), plantMasterDataService.names(PlantMasterDataService.PROCESS_AREA), "Gemba Kaizen Location");
        validateConfigured(record.getClassificationOfKaizen(), kaizenMasterDataService.names(GembaKaizenMasterDataService.CLASSIFICATION_OF_KAIZEN), "Classification of Kaizen");
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

    private void notifyHods(GembaKaizenRecord record, String username, String subject, String body) {
        emailConfigService.sendEmail(hodEmails(record, username), subject, body, true, true);
    }

    private void notifyClosed(GembaKaizenRecord record, String username) {
        List<String> recipients = new ArrayList<>(hodEmails(record, username));
        currentUser(username)
                .map(AppUser::getEmail)
                .filter(email -> !isBlank(email))
                .ifPresent(email -> {
                    if (!recipients.contains(email)) {
                        recipients.add(email);
                    }
                });
        assignedUserEmail(record.getAssignedTo()).ifPresent(email -> {
            if (!recipients.contains(email)) {
                recipients.add(email);
            }
        });
        emailConfigService.sendEmail(recipients, "Gemba Kaizen Closed: #" + record.getId(), buildKaizenEmailBody("Gemba Kaizen Closed", "This Gemba Kaizen has been marked as implemented.", record), true, true);
    }

    private List<String> hodEmails(GembaKaizenRecord record, String username) {
        List<String> recipients = new ArrayList<>();
        currentUser(username).flatMap(this::reportingHodEmail).ifPresent(recipients::add);
        areaHodEmails(record).forEach(email -> {
            if (!recipients.contains(email)) {
                recipients.add(email);
            }
        });
        assignedUserEmail(record.getAssignedTo()).ifPresent(email -> {
            if (!recipients.contains(email)) {
                recipients.add(email);
            }
        });
        return recipients;
    }

    private Optional<String> assignedUserEmail(String assignedTo) {
        String value = trim(assignedTo);
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

    private Optional<String> reportingHodEmail(AppUser user) {
        String manager = trim(user.getReportingManager());
        if (isBlank(manager)) {
            return Optional.empty();
        }
        return userRepository.findByUsernameIgnoreCase(manager)
                .or(() -> userRepository.findByEmailIgnoreCase(manager))
                .or(() -> userRepository.findAll().stream()
                        .filter(candidate -> trim(candidate.getName()).equalsIgnoreCase(manager))
                        .findFirst())
                .map(AppUser::getEmail)
                .filter(email -> !isBlank(email));
    }

    private List<String> areaHodEmails(GembaKaizenRecord record) {
        String location = trim(record.getGembaKaizenLocation()).toLowerCase(Locale.ENGLISH);
        String department = trim(record.getDepartment()).toLowerCase(Locale.ENGLISH);
        return activeUsers().stream()
                .filter(this::isHod)
                .filter(user -> matches(user, location, department))
                .map(AppUser::getEmail)
                .filter(email -> !isBlank(email))
                .distinct()
                .toList();
    }

    private boolean matches(AppUser user, String location, String department) {
        if (location.isBlank() && department.isBlank()) {
            return true;
        }
        String userArea = trim(user.getArea()).toLowerCase(Locale.ENGLISH);
        String userDepartment = trim(user.getDepartment()).toLowerCase(Locale.ENGLISH);
        return (!location.isBlank() && (location.equals(userArea) || location.equals(userDepartment)))
                || (!department.isBlank() && department.equals(userDepartment));
    }

    private List<AppUser> activeUsers() {
        return userRepository.findAll().stream()
                .filter(user -> "ACTIVE".equalsIgnoreCase(trim(user.getStatus())))
                .toList();
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

    private Optional<AppUser> currentUser(String username) {
        if (isBlank(username)) {
            return Optional.empty();
        }
        return userRepository.findByUsernameIgnoreCase(username);
    }

    private boolean isImplemented(GembaKaizenRecord record) {
        return "Yes".equalsIgnoreCase(trim(record.getIsKaizenImplemented()));
    }

    private String normalizeYesNo(String value) {
        return "Yes".equalsIgnoreCase(trim(value)) ? "Yes" : "No";
    }

    private Map<String, String> userOption(AppUser user) {
        return Map.of(
            "username", firstNonBlank(user.getUsername(), user.getEmail()),
                "name", firstNonBlank(user.getName(), user.getUsername()),
            "label", firstNonBlank(user.getName(), user.getUsername()),
                "employeeId", trim(user.getEmployeeId())
        );
    }

    private String buildDailyReportBody(List<GembaKaizenRecord> rows, int reported, int closed) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='margin:0;padding:0;background:#f5f7f9;font-family:Arial,sans-serif;color:#1f2937;'>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='100%' style='background:#f5f7f9;padding:24px 0;'>")
                .append("<tr><td align='center'>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='760' style='max-width:760px;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;'>")
                .append("<tr><td style='background:#003d24;padding:16px 20px;'>")
                .append("<img src='cid:brandLogo' alt='Carlsberg logo' style='height:34px;width:auto;display:block;'>")
                .append("</td></tr>")
                .append("<tr><td style='padding:20px;'>")
                .append("<h2 style='margin:0 0 12px;color:#003d24;font-size:20px;'>Gemba Kaizen Daily Report</h2>")
                .append("<p style='margin:0 0 16px;font-size:14px;'>Reported: ")
                .append(reported)
                .append(" | Closed: ")
                .append(closed)
                .append("</p>")
                .append("<table cellspacing='0' cellpadding='0' border='0' width='100%' style='border-collapse:collapse;font-size:13px;'>")
                .append("<tr>")
                .append(headerCell("Name"))
                .append(headerCell("Department"))
                .append(headerCell("Classification of Kaizen"))
                .append(headerCell("Gemba Kaizen Location"))
                .append(headerCell("Gemba Kaizen Generation Date"))
                .append(headerCell("Is Kaizen Implemented"))
                .append("</tr>");

        for (GembaKaizenRecord row : rows) {
            html.append("<tr>")
                    .append(bodyCell(row.getName()))
                    .append(bodyCell(row.getDepartment()))
                    .append(bodyCell(row.getClassificationOfKaizen()))
                    .append(bodyCell(row.getGembaKaizenLocation()))
                    .append(bodyCell(row.getGembaKaizenGenerationDate() == null ? "" : row.getGembaKaizenGenerationDate().toString()))
                    .append(bodyCell(row.getIsKaizenImplemented()))
                    .append("</tr>");
        }

        html.append("</table>")
                .append("</td></tr>")
                .append("</table>")
                .append("</td></tr></table>")
                .append("</body></html>");
        return html.toString();
    }

    private String buildKaizenEmailBody(String title, String intro, GembaKaizenRecord record) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='margin:0;padding:0;background:#f5f7f9;font-family:Arial,sans-serif;color:#1f2937;'>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='100%' style='background:#f5f7f9;padding:24px 0;'>")
                .append("<tr><td align='center'>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='680' style='max-width:680px;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;'>")
                .append("<tr><td style='background:#003d24;padding:16px 20px;'>")
                .append("<img src='cid:brandLogo' alt='Carlsberg logo' style='height:34px;width:auto;display:block;'>")
                .append("</td></tr><tr><td style='padding:20px;'>")
                .append("<h2 style='margin:0 0 8px;color:#003d24;font-size:20px;'>")
                .append(escapeHtml(title))
                .append("</h2>")
                .append("<p style='margin:0 0 16px;font-size:14px;line-height:1.5;'>")
                .append(escapeHtml(intro))
                .append("</p>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='100%' style='border-collapse:collapse;font-size:14px;'>")
                .append(detailRow("Kaizen ID", record.getId() == null ? "-" : "#" + record.getId()))
                .append(detailRow("Name", record.getName()))
                .append(detailRow("Provider Name", record.getGembaKaizenProviderName()))
                .append(detailRow("Employee ID / HO Number", record.getEmployeeIdHoNumber()))
                .append(detailRow("Department", record.getDepartment()))
                .append(detailRow("Classification", record.getClassificationOfKaizen()))
                .append(detailRow("Location", record.getGembaKaizenLocation()))
                .append(detailRow("Generation Date", record.getGembaKaizenGenerationDate() == null ? "" : record.getGembaKaizenGenerationDate().toString()))
                .append(detailRow("Kaizen Idea", record.getKaizenIdea()))
                .append(detailRow("Benefits", record.getBenefitsOfKaizen()))
                .append(detailRow("Implemented", record.getIsKaizenImplemented()))
                .append(detailRow("Assigned To", record.getAssignedTo()))
                .append("</table>")
                .append("<p style='margin:16px 0 0;font-size:13px;color:#6b7280;'>Please review and update the Gemba Kaizen record in PMS.</p>")
                .append("<p style='margin:16px 0 0;font-size:13px;color:#6b7280;'>Regards,<br>Brewery PMS</p>")
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

    private String headerCell(String value) {
        return "<th style='padding:8px;border:1px solid #e5e7eb;background:#f9fafb;color:#374151;text-align:left;'>" + escapeHtml(value) + "</th>";
    }

    private String bodyCell(String value) {
        return "<td style='padding:8px;border:1px solid #e5e7eb;color:#111827;'>" + escapeHtml(value) + "</td>";
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
