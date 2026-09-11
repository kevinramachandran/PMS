package org.example.service;

import org.example.entity.AbnormalityReportingRecord;
import org.example.entity.AppUser;
import org.example.repository.AbnormalityReportingRecordRepository;
import org.example.repository.AppUserRepository;
import org.example.util.RoleAccess;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class AbnormalityReportingConfigService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<String> PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> SHIFTS = Set.of("A", "B", "C", "G");
    private static final Set<String> TAG_STATUSES = Set.of("OPEN", "CLOSED");

    private final AbnormalityReportingRecordRepository repository;
    private final AbnormalityMasterDataService abnormalityMasterDataService;
    private final PlantMasterDataService plantMasterDataService;
    private final AppUserRepository appUserRepository;
    private final EmailConfigService emailConfigService;
    private final AssignmentHistoryService assignmentHistoryService;

    public AbnormalityReportingConfigService(AbnormalityReportingRecordRepository repository,
                                             AbnormalityMasterDataService abnormalityMasterDataService,
                                             PlantMasterDataService plantMasterDataService,
                                             AppUserRepository appUserRepository,
                                             EmailConfigService emailConfigService,
                                             AssignmentHistoryService assignmentHistoryService) {
        this.repository = repository;
        this.abnormalityMasterDataService = abnormalityMasterDataService;
        this.plantMasterDataService = plantMasterDataService;
        this.appUserRepository = appUserRepository;
        this.emailConfigService = emailConfigService;
        this.assignmentHistoryService = assignmentHistoryService;
    }

    public List<AbnormalityReportingRecord> list() {
        return repository.findAllByOrderByDateRaisedDescIdDesc();
    }

    public Optional<AbnormalityReportingRecord> find(Long id) {
        return id == null ? Optional.empty() : repository.findById(id);
    }

    public List<AbnormalityReportingRecord> listForUser(String username, String role) {
        List<AbnormalityReportingRecord> rows = list();
        if (RoleAccess.isAdmin(role)) {
            return rows;
        }
        Optional<AppUser> current = currentUser(username);
        if (current.isEmpty()) {
            return List.of();
        }
        AppUser user = current.get();
        return rows.stream().filter(record -> canSeeRecord(record, user)).toList();
    }

    public Optional<AbnormalityReportingRecord> findForUser(Long id, String username, String role) {
        return find(id)
                .filter(record -> RoleAccess.isAdmin(role)
                        || currentUser(username).filter(user -> canSeeRecord(record, user)).isPresent());
    }

    @Transactional
    public AbnormalityReportingRecord create(AbnormalityReportingRecord request, String username, String role) {
        AbnormalityReportingRecord record = new AbnormalityReportingRecord();
        apply(record, request, username, role, true);
        applyClosedDate(record);
        AbnormalityReportingRecord saved = repository.save(record);
        assignmentHistoryService.record("abnormality-reporting", saved.getId(), "", saved.getAssignTo(), request.getAssignmentRemark(), username, saved.getDepartment());
        notifyDepartmentHod(saved, "Abnormality Report Raised");
        return saved;
    }

    @Transactional
    public Optional<AbnormalityReportingRecord> update(Long id, AbnormalityReportingRecord request, String username, String role) {
        Optional<AbnormalityReportingRecord> existing = findForUser(id, username, role);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        AbnormalityReportingRecord record = existing.get();
        AppUser actor = currentUser(username).orElse(null);
        if (!RoleAccess.isAdmin(role) && !canUpdateRecord(record, actor)) {
            throw new IllegalArgumentException("You can update only abnormalities raised by you, assigned to you, or within your permitted area");
        }
        String previousAssignee = record.getAssignTo();
        boolean wasClosed = isClosed(record.getTagStatus());
        apply(record, request, username, role, false);
        applyClosedDate(record);
        AbnormalityReportingRecord saved = repository.save(record);
        assignmentHistoryService.record("abnormality-reporting", saved.getId(), previousAssignee, saved.getAssignTo(), request.getAssignmentRemark(), username, saved.getDepartment());
        if (!wasClosed && isClosed(saved.getTagStatus())) {
            notifyClosure(saved);
        }
        return Optional.of(saved);
    }

    public Map<String, Object> options(String username, String role, String department, String areaMachine, Long recordId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Optional<AppUser> current = currentUser(username);
        payload.put("typeOfTags", abnormalityMasterDataService.names(AbnormalityMasterDataService.ABT_TAG_TYPE));
        payload.put("plants", plantMasterDataService.names(PlantMasterDataService.PLANT));
        payload.put("departments", plantMasterDataService.names(PlantMasterDataService.DEPARTMENT));
        payload.put("areaMachines", plantMasterDataService.names(PlantMasterDataService.PROCESS_AREA));
        payload.put("plantItems", plantMasterDataService.list(PlantMasterDataService.PLANT));
        payload.put("departmentItems", plantMasterDataService.list(PlantMasterDataService.DEPARTMENT));
        payload.put("areaItems", plantMasterDataService.list(PlantMasterDataService.PROCESS_AREA));
        payload.put("abnormalityDefectTypes", abnormalityMasterDataService.names(AbnormalityMasterDataService.ABNORMALITY_DEFECT_TYPE));
        AbnormalityReportingRecord record = recordId == null ? null : repository.findById(recordId).orElse(null);
        payload.put("currentUser", current.map(this::userOption).orElse(Map.of()));
        payload.put("assignableUsers", userOptions(assignableUsers(department, areaMachine, current.orElse(null), role, record)));
        payload.put("defaultAssignee", defaultAreaHod(department, areaMachine).map(user -> defaultText(user.getUsername(), user.getName())).orElse(""));
        return payload;
    }

    public Map<String, Object> departmentOptions(String username, String role, String department, String areaMachine, Long recordId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Optional<AppUser> current = currentUser(username);
        AbnormalityReportingRecord record = recordId == null ? null : repository.findById(recordId).orElse(null);
        payload.put("assignableUsers", userOptions(assignableUsers(department, areaMachine, current.orElse(null), role, record)));
        payload.put("defaultAssignee", defaultAreaHod(department, areaMachine).map(user -> defaultText(user.getUsername(), user.getName())).orElse(""));
        return payload;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "${app.timezone:Asia/Calcutta}")
    public void sendDailyDashboardReport() {
        if (!emailConfigService.isAbnormalityReportingDailyEnabled()) {
            return;
        }
        List<AbnormalityReportingRecord> rows = list();
        int closed = (int) rows.stream().filter(row -> isClosed(row.getTagStatus())).count();
        emailConfigService.sendEmail(
                emailConfigService.configuredReportRecipients(),
                "Abnormality Reporting Daily Report",
                buildDailyReportBody(rows, rows.size(), closed),
                true,
                true
        );
    }

    private void apply(AbnormalityReportingRecord record, AbnormalityReportingRecord request, String username, String role, boolean forceCurrentUser) {
        if (request == null) {
            throw new IllegalArgumentException("Record is required");
        }
        validateConfigured(request.getTypeOfTag(), abnormalityMasterDataService.names(AbnormalityMasterDataService.ABT_TAG_TYPE), "Type of Tag");
        validateInSet(request.getPriority(), PRIORITIES, "Priority");
        validateRequired(request.getDateRaised(), "Date Raised");
        validateInSet(request.getShift(), SHIFTS, "Shift");
        validateConfigured(request.getDepartment(), plantMasterDataService.names(PlantMasterDataService.DEPARTMENT), "Department");
        validateConfigured(request.getAreaMachine(), plantMasterDataService.names(PlantMasterDataService.PROCESS_AREA), "Area/Machine");
        validateRequired(request.getComponent(), "Component");
        validateRequired(request.getDescription(), "Description");
        validateRequired(request.getProposedAction(), "Proposed Action");
        validateConfigured(request.getAbnormalityDefectType(), abnormalityMasterDataService.names(AbnormalityMasterDataService.ABNORMALITY_DEFECT_TYPE), "Abnormality/Defect Type");
        String requestedAssignee = trim(request.getAssignTo());
        if (isBlank(requestedAssignee) && record.getId() == null) {
            requestedAssignee = defaultAreaHod(request.getDepartment(), request.getAreaMachine())
                    .map(user -> defaultText(user.getUsername(), user.getName()))
                    .orElse("");
        }
        validateAssignee(requestedAssignee, request.getDepartment(), request.getAreaMachine(),
                currentUser(username).orElse(null), role, record);
        validateInSet(request.getTagStatus(), TAG_STATUSES, "Tag Status");
        record.setTypeOfTag(trim(request.getTypeOfTag()));
        record.setPriority(trim(request.getPriority()));
        if (record.getId() == null) {
            record.setAbnormalityTagNumber("");
        }
        if (forceCurrentUser || isBlank(record.getTagRaisedBy())) {
            record.setTagRaisedBy(currentUser(username)
                    .map(user -> defaultText(user.getName(), user.getUsername()))
                    .orElse(defaultText(username, "")));
        }
        record.setDateRaised(request.getDateRaised());
        record.setShift(trim(request.getShift()));
        if (record.getId() == null) {
            record.setAbnormalityRelatedTo("");
        }
        record.setDepartment(trim(request.getDepartment()));
        record.setAreaMachine(trim(request.getAreaMachine()));
        record.setComponent(trim(request.getComponent()));
        record.setDescription(trim(request.getDescription()));
        record.setProposedAction(trim(request.getProposedAction()));
        record.setPictureImage(trim(request.getPictureImage()));
        record.setAbnormalityDefectType(trim(request.getAbnormalityDefectType()));
        record.setAssignTo(requestedAssignee);
        record.setDateClosed(request.getDateClosed());
        record.setTagStatus(trim(request.getTagStatus()));
    }

    private void applyClosedDate(AbnormalityReportingRecord record) {
        if (isClosed(record.getTagStatus()) && record.getDateClosed() == null) {
            record.setDateClosed(LocalDate.now());
        }
        if (!isClosed(record.getTagStatus())) {
            record.setDateClosed(null);
        }
    }

    private void validateConfigured(String value, List<String> options, String label) {
        String trimmed = trim(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        boolean configured = options.stream()
                .anyMatch(option -> option != null && option.trim().equalsIgnoreCase(trimmed));
        if (!configured) {
            throw new IllegalArgumentException(label + " must be configured in Master Data");
        }
    }

    private void validateRequired(String value, String label) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private void validateRequired(LocalDate value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private void validateInSet(String value, Set<String> allowedValues, String label) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (!allowedValues.contains(normalized)) {
            throw new IllegalArgumentException(label + " is invalid");
        }
    }

    private void validateAssignee(String username, String department, String areaMachine, AppUser actor, String role, AbnormalityReportingRecord record) {
        validateRequired(username, "Assign To");
        String previousAssignee = record == null ? "" : trim(record.getAssignTo());
        boolean assigneeChanged = !defaultText(previousAssignee, "").equalsIgnoreCase(defaultText(username, ""));
        if (assigneeChanged && record != null && record.getId() != null && !RoleAccess.isAdmin(role) && !isAreaHod(actor)) {
            throw new IllegalArgumentException("Only the Area HoD can reassign this Abnormality Report");
        }
        boolean valid = assignableUsers(department, areaMachine, actor, role, record).stream()
                .anyMatch(user -> equalsIgnoreCase(user.getUsername(), username)
                        || equalsIgnoreCase(user.getName(), username)
                        || equalsIgnoreCase(user.getEmail(), username)
                        || equalsIgnoreCase(user.getEmployeeId(), username));
        if (!valid) {
            throw new IllegalArgumentException("Assign To must be a permitted workflow user");
        }
    }

    private void notifyDepartmentHod(AbnormalityReportingRecord record, String subjectPrefix) {
        List<String> recipients = emails(findDepartmentHods(record.getDepartment()));
        if (recipients.isEmpty()) {
            return;
        }
        emailConfigService.sendEmail(recipients, subjectPrefix + ": " + defaultText(record.getAbnormalityTagNumber(), "Abnormality Report"), buildEmailBody(record), true, true);
    }

    private void notifyClosure(AbnormalityReportingRecord record) {
        LinkedHashSet<String> recipients = new LinkedHashSet<>(emails(findDepartmentHods(record.getDepartment())));
        resolveUser(record.getTagRaisedBy()).map(AppUser::getEmail).filter(this::hasText).ifPresent(recipients::add);
        if (recipients.isEmpty()) {
            return;
        }
        emailConfigService.sendEmail(new ArrayList<>(recipients), "Abnormality Report Closed: " + defaultText(record.getAbnormalityTagNumber(), "Abnormality Report"), buildEmailBody(record), true, true);
    }

    private List<AppUser> findDepartmentHods(String department) {
        List<AppUser> hods = appUserRepository.findAll().stream()
                .filter(this::isActive)
                .filter(this::isHod)
                .toList();
        if (isBlank(department)) {
            return hods;
        }
        List<AppUser> scoped = hods.stream()
                .filter(user -> sameMasterValue(user.getDepartment(), department))
                .toList();
        return scoped.isEmpty() ? hods : scoped;
    }

    private List<AppUser> assignableUsers(String department, String areaMachine, AppUser actor, String role, AbnormalityReportingRecord record) {
        List<AppUser> scoped = activeUsers().stream()
                .filter(user -> matchesScope(user, department, areaMachine))
                .toList();
        List<AppUser> pool = scoped.isEmpty() ? activeUsers() : scoped;
        if (RoleAccess.isAdmin(role)) {
            return pool.stream()
                    .filter(user -> isHod(user) || isOperational(user))
                    .toList();
        }
        boolean existingAssignment = record != null && !isBlank(record.getAssignTo());
        boolean actorIsAreaHod = isAreaHod(actor);
        return pool.stream()
                .filter(user -> {
                    if (!existingAssignment) {
                        return isAreaHod(user);
                    }
                    if (actorIsAreaHod) {
                        return isOperational(user);
                    }
                    return matchesUser(user, record.getAssignTo());
                })
                .toList();
    }

    private Optional<AppUser> defaultAreaHod(String department, String areaMachine) {
        return activeUsers().stream()
                .filter(user -> matchesScope(user, department, areaMachine))
                .filter(this::isAreaHod)
                .findFirst();
    }

    private boolean canSeeRecord(AbnormalityReportingRecord record, AppUser user) {
        if (record == null || user == null) {
            return false;
        }
        return matchesUser(user, record.getTagRaisedBy()) || matchesUser(user, record.getAssignTo());
    }

    private boolean canUpdateRecord(AbnormalityReportingRecord record, AppUser user) {
        return canSeeRecord(record, user)
                && (isAreaHod(user) || matchesUser(user, record.getTagRaisedBy()) || matchesUser(user, record.getAssignTo()));
    }

    private List<AppUser> activeUsers() {
        return appUserRepository.findAll().stream()
                .filter(this::isActive)
                .toList();
    }

    private boolean isHod(AppUser user) {
        if (user == null) {
            return false;
        }
        String designation = normalize(user.getDesignation());
        return RoleAccess.isHod(user.getRole())
                || designation.contains("HOD")
                || designation.contains("HEAD_OF_DEPARTMENT")
                || designation.contains("DEPARTMENT_HEAD")
                || designation.contains("AREA_HEAD");
    }

    private boolean isAreaHod(AppUser user) {
        if (user == null) {
            return false;
        }
        String role = RoleAccess.normalize(user.getRole());
        String designation = normalize(user.getDesignation());
        return RoleAccess.AREA_HOD.equals(role)
                || designation.contains("AREA_HOD")
                || designation.contains("AREA_HEAD");
    }

    private boolean isOperational(AppUser user) {
        if (user == null) {
            return false;
        }
        String role = RoleAccess.normalize(user.getRole());
        String designation = normalize(user.getDesignation());
        return RoleAccess.isAssignableOperationalRole(role)
                || RoleAccess.isAssignableOperationalRole(designation);
    }

    private List<Map<String, String>> userOptions(List<AppUser> users) {
        return users.stream()
                .map(this::userOption)
                .toList();
    }

    private Map<String, String> userOption(AppUser user) {
        Map<String, String> option = new LinkedHashMap<>();
        option.put("username", defaultText(user.getUsername(), ""));
        option.put("name", defaultText(user.getName(), ""));
        option.put("email", defaultText(user.getEmail(), ""));
        option.put("department", defaultText(user.getDepartment(), ""));
        option.put("area", defaultText(user.getArea(), ""));
        option.put("designation", defaultText(user.getDesignation(), ""));
        option.put("role", RoleAccess.normalize(user.getRole()));
        return option;
    }

    private List<String> emails(List<AppUser> users) {
        return users.stream().map(AppUser::getEmail).filter(this::hasText).distinct().toList();
    }

    private Optional<AppUser> resolveUser(String value) {
        String normalized = trim(value);
        if (normalized == null) {
            return Optional.empty();
        }
        Optional<AppUser> byUsername = appUserRepository.findByUsernameIgnoreCase(normalized);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        Optional<AppUser> byEmail = appUserRepository.findByEmailIgnoreCase(normalized);
        if (byEmail.isPresent()) {
            return byEmail;
        }
        return appUserRepository.findAll().stream()
                .filter(user -> equalsIgnoreCase(user.getName(), normalized))
                .findFirst();
    }

    private Optional<AppUser> currentUser(String username) {
        return resolveUser(username);
    }

    private boolean isActive(AppUser user) {
        return user != null && !"INACTIVE".equals(normalize(user.getStatus()));
    }

    private boolean isClosed(String status) {
        String normalized = normalize(status);
        return "CLOSED".equals(normalized) || "CLOSE".equals(normalized);
    }

    private String buildEmailBody(AbnormalityReportingRecord record) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='margin:0;padding:0;background:#f5f7f9;font-family:Arial,sans-serif;color:#1f2937;'>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='100%' style='background:#f5f7f9;padding:24px 0;'>")
                .append("<tr><td align='center'>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='680' style='max-width:680px;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;'>")
                .append("<tr><td style='background:#003d24;padding:16px 20px;'>")
                .append("<img src='cid:brandLogo' alt='Carlsberg logo' style='height:34px;width:auto;display:block;'>")
                .append("</td></tr><tr><td style='padding:20px;'>")
                .append("<h2 style='margin:0 0 12px 0;color:#003d24;font-size:20px;'>Abnormality Reporting</h2>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='100%' style='border-collapse:collapse;font-size:14px;'>")
                .append(row("Type of Tag", record.getTypeOfTag()))
                .append(row("Priority", record.getPriority()))
                .append(row("Tag Raised By", record.getTagRaisedBy()))
                .append(row("Date Raised", formatDate(record.getDateRaised())))
                .append(row("Shift", record.getShift()))
                .append(row("Department", record.getDepartment()))
                .append(row("Area/Machine", record.getAreaMachine()))
                .append(row("Component", record.getComponent()))
                .append(row("Description", record.getDescription()))
                .append(row("Proposed Action", record.getProposedAction()))
                .append(row("Abnormality/Defect Type", record.getAbnormalityDefectType()))
                .append(row("Assign To", record.getAssignTo()))
                .append(row("Date Closed", formatDate(record.getDateClosed())))
                .append(row("Tag Status", record.getTagStatus()))
                .append("</table>")
                .append("<p style='margin:16px 0 0 0;font-size:13px;color:#6b7280;'>Regards,<br>Brewery PMS</p>")
                .append("</td></tr></table></td></tr></table></body></html>");
        return html.toString();
    }

    private String buildDailyReportBody(List<AbnormalityReportingRecord> rows, int reported, int closed) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='margin:0;padding:0;background:#f5f7f9;font-family:Arial,sans-serif;color:#1f2937;'>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='100%' style='background:#f5f7f9;padding:24px 0;'>")
                .append("<tr><td align='center'>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='760' style='max-width:760px;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;'>")
                .append("<tr><td style='background:#003d24;padding:16px 20px;'>")
                .append("<img src='cid:brandLogo' alt='Carlsberg logo' style='height:34px;width:auto;display:block;'>")
                .append("</td></tr><tr><td style='padding:20px;'>")
                .append("<h2 style='margin:0 0 12px 0;color:#003d24;font-size:20px;'>Abnormality Reporting Daily Report</h2>")
                .append("<p style='margin:0 0 16px;font-size:14px;'>Reported: ")
                .append(reported)
                .append(" | Closed: ")
                .append(closed)
                .append("</p>")
                .append("<table cellspacing='0' cellpadding='0' border='0' width='100%' style='border-collapse:collapse;font-size:13px;'>")
                .append("<tr>")
                .append(headerCell("Type of Tag"))
                .append(headerCell("Department"))
                .append(headerCell("Date Raised"))
                .append(headerCell("Tag Status"))
                .append("</tr>");
        for (AbnormalityReportingRecord row : rows) {
            html.append("<tr>")
                    .append(bodyCell(row.getTypeOfTag()))
                    .append(bodyCell(row.getDepartment()))
                    .append(bodyCell(formatDate(row.getDateRaised())))
                    .append(bodyCell(row.getTagStatus()))
                    .append("</tr>");
        }
        html.append("</table></td></tr></table></td></tr></table></body></html>");
        return html.toString();
    }

    private String headerCell(String value) {
        return "<th style='padding:8px;border:1px solid #e5e7eb;background:#f9fafb;color:#374151;text-align:left;'>"
                + escape(value)
                + "</th>";
    }

    private String bodyCell(String value) {
        return "<td style='padding:8px;border:1px solid #e5e7eb;color:#111827;'>"
                + escape(defaultText(value, "-"))
                + "</td>";
    }

    private String row(String label, String value) {
        return "<tr><td style='padding:8px 10px;background:#f9fafb;border:1px solid #e5e7eb;width:190px;color:#374151;font-weight:600;'>"
                + escape(label)
                + "</td><td style='padding:8px 10px;border:1px solid #e5e7eb;color:#111827;'>"
                + escape(defaultText(value, "-"))
                + "</td></tr>";
    }

    private String formatDate(LocalDate date) {
        return date == null ? "-" : DATE_FORMATTER.format(date);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return trim(left) != null && trim(right) != null && trim(left).equalsIgnoreCase(trim(right));
    }

    private boolean sameMasterValue(String left, String right) {
        String first = compact(left);
        String second = compact(right);
        return !first.isBlank() && !second.isBlank() && first.equals(second);
    }

    private boolean matchesScope(AppUser user, String department, String areaMachine) {
        if (user == null) {
            return false;
        }
        String expectedDepartment = compact(department);
        String expectedArea = compact(areaMachine);
        boolean departmentMatches = expectedDepartment.isBlank() || expectedDepartment.equals(compact(user.getDepartment()));
        boolean areaMatches = expectedArea.isBlank() || matchesAnyArea(user.getArea(), expectedArea);
        return departmentMatches && areaMatches;
    }

    private boolean matchesAnyArea(String userAreas, String expectedArea) {
        return List.of(trim(userAreas).split(",")).stream()
                .map(this::compact)
                .anyMatch(expectedArea::equals);
    }

    private boolean matchesUser(AppUser user, String value) {
        String expected = compactUser(value);
        if (user == null || expected.isBlank()) {
            return false;
        }
        return expected.equals(compactUser(user.getUsername()))
                || expected.equals(compactUser(user.getName()))
                || expected.equals(compactUser(user.getEmail()))
                || expected.equals(compactUser(user.getEmployeeId()));
    }

    private String compact(String value) {
        String trimmed = trim(value);
        if (trimmed == null) {
            return "";
        }
        return trimmed.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private String compactUser(String value) {
        String trimmed = trim(value);
        if (trimmed == null) {
            return "";
        }
        return trimmed.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9@.]", "");
    }

    private String normalize(String value) {
        return defaultText(value, "").trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private String defaultText(String value, String fallback) {
        String trimmed = trim(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return trim(value) == null;
    }

    private boolean hasText(String value) {
        return !isBlank(value);
    }

    private String escape(String value) {
        return defaultText(value, "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
