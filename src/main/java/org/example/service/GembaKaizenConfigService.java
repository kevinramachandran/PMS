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
import java.util.Set;

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

    public List<GembaKaizenRecord> listForUser(String username, String role) {
        return list();
    }

    public Optional<GembaKaizenRecord> find(Long id) {
        return repository.findById(id);
    }

    public Optional<GembaKaizenRecord> findForUser(Long id, String username, String role) {
        return find(id)
                .filter(record -> RoleAccess.isAdmin(role)
                        || currentUser(username).filter(user -> canSeeRecord(record, user)).isPresent());
    }

    @Transactional
    public GembaKaizenRecord create(GembaKaizenRecord record, String username, String role) {
        if (record.getId() != null) throw new IllegalArgumentException("Use Update to change an existing record");
        applyDefaults(record, username, true);
        assignmentHistoryService.validateTransition(true, "", record.getAssignedTo(), "", record.getReassignedTo1(), record.getReassignment1Remark(), "", record.getReassignedTo2(), record.getReassignment2Remark());
        validateAssignedTo(record.getAssignedTo(), record.getDepartment(), record.getGembaKaizenLocation(),
                currentUser(username).orElse(null), role, "", null);
        validateAssignedTo(record.getReassignedTo1(), record.getDepartment(), record.getGembaKaizenLocation(), currentUser(username).orElse(null), role, record.getAssignedTo(), record);
        validateAssignedTo(record.getReassignedTo2(), record.getDepartment(), record.getGembaKaizenLocation(), currentUser(username).orElse(null), role, record.getReassignedTo1(), record);
        assignmentHistoryService.validateSlots(record.getReassignedTo1(), record.getReassignment1Remark(), record.getReassignedTo2(), record.getReassignment2Remark());
        GembaKaizenRecord saved = repository.save(record);
        assignmentHistoryService.record("gemba-kaizen", saved.getId(), "", saved.getAssignedTo(), saved.getAssignmentRemark(), username, saved.getGembaKaizenLocation());
        NotificationDispatch.afterCommit(() -> notifyHods(saved, username, "Gemba Kaizen Submitted: #" + saved.getId(), buildKaizenEmailBody("Gemba Kaizen Submitted", "A Gemba Kaizen idea has been submitted for review.", saved)));
        if (isImplemented(saved)) {
            NotificationDispatch.afterCommit(() -> notifyClosed(saved, username));
        }
        return saved;
    }

    @Transactional
    public Optional<GembaKaizenRecord> update(Long id, GembaKaizenRecord incoming, String username, String role) {
        return findForUser(id, username, role).map(existing -> {
            AppUser actor = currentUser(username).orElse(null);
            if (!RoleAccess.isAdmin(role) && !canUpdateRecord(existing, actor)) {
                throw new IllegalArgumentException("You can update only Gemba Kaizens created by you or assigned to you");
            }
            String previousAssignee = existing.getAssignedTo();
            String previousFirst = existing.getReassignedTo1();
            String previousSecond = existing.getReassignedTo2();
            assignmentHistoryService.validateTransition(false, previousAssignee, incoming.getAssignedTo(), previousFirst, incoming.getReassignedTo1(), incoming.getReassignment1Remark(), previousSecond, incoming.getReassignedTo2(), incoming.getReassignment2Remark());
            boolean wasImplemented = isImplemented(existing);
            existing.setPictureImage(trim(incoming.getPictureImage()));
            existing.setIsKaizenImplemented(normalizeYesNo(incoming.getIsKaizenImplemented()));
            String requestedAssignee = trim(incoming.getAssignedTo());
            validateAssignedTo(requestedAssignee, existing.getDepartment(), existing.getGembaKaizenLocation(),
                    actor, role, previousAssignee, existing);
            validateAssignedTo(incoming.getReassignedTo1(), existing.getDepartment(), existing.getGembaKaizenLocation(), actor, role, previousFirst, existing);
            validateAssignedTo(incoming.getReassignedTo2(), existing.getDepartment(), existing.getGembaKaizenLocation(), actor, role, previousSecond, existing);
            assignmentHistoryService.validateSlots(incoming.getReassignedTo1(), incoming.getReassignment1Remark(), incoming.getReassignedTo2(), incoming.getReassignment2Remark());
            existing.setAssignedTo(requestedAssignee);
            existing.setReassignedTo1(trim(incoming.getReassignedTo1()));
            existing.setReassignment1Remark(trim(incoming.getReassignment1Remark()));
            existing.setReassignedTo2(trim(incoming.getReassignedTo2()));
            existing.setReassignment2Remark(trim(incoming.getReassignment2Remark()));
            existing.setAssignmentRemark(incoming.getAssignmentRemark());
            applyDefaults(existing, username, false);
            GembaKaizenRecord saved = repository.save(existing);
            assignmentHistoryService.record("gemba-kaizen", saved.getId(), previousAssignee, saved.getAssignedTo(), incoming.getAssignmentRemark(), username, saved.getGembaKaizenLocation());
            assignmentHistoryService.recordStages("gemba-kaizen", saved.getId(), saved.getAssignedTo(), previousFirst, saved.getReassignedTo1(), saved.getReassignment1Remark(), previousSecond, saved.getReassignedTo2(), saved.getReassignment2Remark(), username, saved.getGembaKaizenLocation());
            if (!trim(previousFirst).equalsIgnoreCase(trim(saved.getReassignedTo1()))) {
                NotificationDispatch.afterCommit(() -> assignedUserEmail(saved.getReassignedTo1()).ifPresent(email ->
                        emailConfigService.sendEmail(List.of(email), "Gemba Kaizen Reassigned: #" + saved.getId(), buildKaizenEmailBody("Gemba Kaizen Reassigned", "A Gemba Kaizen has been assigned to you.", saved), true, true)));
            }
            if (!trim(previousSecond).equalsIgnoreCase(trim(saved.getReassignedTo2()))) {
                NotificationDispatch.afterCommit(() -> assignedUserEmail(saved.getReassignedTo2()).ifPresent(email ->
                        emailConfigService.sendEmail(List.of(email), "Gemba Kaizen Reassigned: #" + saved.getId(), buildKaizenEmailBody("Gemba Kaizen Reassigned", "A Gemba Kaizen has been assigned to you.", saved), true, true)));
            }
            if (!wasImplemented && isImplemented(saved)) {
                NotificationDispatch.afterCommit(() -> notifyClosed(saved, username));
            }
            return saved;
        });
    }

    @Transactional
    public boolean delete(Long id, String username, String role) {
        Optional<GembaKaizenRecord> record = findForUser(id, username, role);
        if (record.isEmpty()) return false;
        if (!RoleAccess.isAdmin(role) && !canUpdateRecord(record.get(), currentUser(username).orElse(null))) {
            throw new IllegalArgumentException("You can delete only records you are allowed to edit" );
        }
        repository.deleteById(id);
        return true;
    }

    public Map<String, Object> options(String username, String role, String department, String location, Long recordId) {
        Map<String, Object> options = new LinkedHashMap<>();
        Optional<AppUser> current = currentUser(username);
        options.put("currentUser", current.map(this::userOption).orElse(Map.of()));
        options.put("plants", plantMasterDataService.names(PlantMasterDataService.PLANT));
        options.put("plantItems", plantMasterDataService.list(PlantMasterDataService.PLANT));
        options.put("departments", plantMasterDataService.names(PlantMasterDataService.DEPARTMENT));
        options.put("departmentItems", plantMasterDataService.list(PlantMasterDataService.DEPARTMENT));
        options.put("processAreas", plantMasterDataService.names(PlantMasterDataService.PROCESS_AREA));
        options.put("areaItems", plantMasterDataService.list(PlantMasterDataService.PROCESS_AREA));
        options.put("classifications", kaizenMasterDataService.names(GembaKaizenMasterDataService.CLASSIFICATION_OF_KAIZEN));
        GembaKaizenRecord record = recordId == null ? null : repository.findById(recordId).orElse(null);
        String resolvedLocation = record == null ? location : record.getGembaKaizenLocation();
        String resolvedDepartment = record == null
                ? deriveDepartment(location, department, current.map(AppUser::getDepartment).orElse(""))
                : record.getDepartment();
        options.put("assignmentUsers", assignmentUsers(resolvedDepartment, resolvedLocation, current.orElse(null), role, record));
        options.put("defaultAssignedTo", defaultAreaHod(resolvedDepartment, resolvedLocation).map(user -> firstNonBlank(user.getUsername(), user.getName())).orElse(""));
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

    private void applyDefaults(GembaKaizenRecord record, String username, boolean forceUserIdentity) {
        currentUser(username).ifPresent(user -> {
            if (forceUserIdentity || isBlank(record.getName())) {
                record.setName(firstNonBlank(user.getName(), user.getUsername()));
            }
            if (forceUserIdentity || isBlank(record.getEmployeeIdHoNumber())) {
                record.setEmployeeIdHoNumber(trim(user.getEmployeeId()));
            }
        });
        if (forceUserIdentity) {
            record.setGembaKaizenProviderName("");
        }
        if (isBlank(record.getLastModifiedTime())) {
            record.setLastModifiedTime(LocalTime.now().format(TIME_FORMATTER));
        }
        if (record.getGembaKaizenGenerationDate() == null) {
            record.setGembaKaizenGenerationDate(LocalDate.now());
        }
        record.setDepartment(deriveDepartment(record.getGembaKaizenLocation(), record.getDepartment(),
                currentUser(username).map(AppUser::getDepartment).orElse("")));
        if (isBlank(record.getAssignedTo())) {
            defaultAreaHod(record.getDepartment(), record.getGembaKaizenLocation()).ifPresent(user ->
                    record.setAssignedTo(firstNonBlank(user.getUsername(), user.getName())));
        }
        record.setIsKaizenImplemented(normalizeYesNo(record.getIsKaizenImplemented()));
        if (forceUserIdentity) {
            validateConfigured(record.getDepartment(), plantMasterDataService.names(PlantMasterDataService.DEPARTMENT), "Department");
            validateConfigured(record.getGembaKaizenLocation(), plantMasterDataService.names(PlantMasterDataService.PROCESS_AREA), "Gemba Kaizen Location");
            validateConfigured(record.getClassificationOfKaizen(), kaizenMasterDataService.names(GembaKaizenMasterDataService.CLASSIFICATION_OF_KAIZEN), "Classification of Kaizen");
        }
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
        return activeUsers().stream()
                .filter(this::isHod)
                .filter(user -> matchesScope(user, record.getDepartment(), record.getGembaKaizenLocation()))
                .map(AppUser::getEmail)
                .filter(email -> !isBlank(email))
                .distinct()
                .toList();
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

    private List<Map<String, String>> assignmentUsers(String department, String location, AppUser actor, String role, GembaKaizenRecord record) {
        List<AppUser> scoped = activeUsers().stream()
                .filter(user -> matchesScope(user, department, location))
                .toList();
        boolean hasScope = !isBlank(department) || !isBlank(location);
        List<AppUser> pool = hasScope ? scoped : activeUsers();
        if (!RoleAccess.isAdmin(role) && (record == null || isBlank(record.getAssignedTo()))) {
            return pool.stream()
                    .filter(this::isAreaHod)
                    .map(this::userOption)
                    .toList();
        }
        return pool.stream()
                .map(this::userOption)
                .toList();
    }

    private void validateAssignedTo(String value, String department, String location, AppUser actor, String role, String previousAssignee, GembaKaizenRecord record) {
        String trimmed = trim(value);
        if (record != null && record.getId() != null && trimmed.equalsIgnoreCase(trim(previousAssignee))) return;
        if (trimmed.isBlank()) {
            return;
        }
        boolean configured = assignmentUsers(department, location, actor, role, record).stream()
                .anyMatch(user -> trim(user.get("username")).equalsIgnoreCase(trimmed)
                        || trim(user.get("label")).equalsIgnoreCase(trimmed));
        if (!configured) {
            throw new IllegalArgumentException("Assigned To must be a permitted workflow user");
        }
    }

    private Optional<AppUser> defaultAreaHod(String department, String location) {
        return activeUsers().stream()
                .filter(this::isAreaHod)
                .filter(user -> matchesScope(user, department, location))
                .findFirst();
    }

    private boolean isAreaHod(AppUser user) {
        if (user == null) {
            return false;
        }
        String role = RoleAccess.normalize(user.getRole());
        String designation = trim(user.getDesignation()).toUpperCase(Locale.ENGLISH);
        return RoleAccess.AREA_HOD.equals(role)
                || designation.contains("AREA HOD")
                || designation.contains("AREA_HOD")
                || designation.contains("AREA HEAD")
                || isHod(user);
    }

    private boolean canSeeRecord(GembaKaizenRecord record, AppUser user) {
        if (record == null || user == null) {
            return false;
        }
        return matchesUser(user, record.getName())
                || matchesUser(user, record.getGembaKaizenProviderName())
                || matchesUser(user, record.getEmployeeIdHoNumber())
                || matchesUser(user, record.getAssignedTo())
                || matchesUser(user, record.getReassignedTo1())
                || matchesUser(user, record.getReassignedTo2());
    }

    private boolean canUpdateRecord(GembaKaizenRecord record, AppUser user) {
        return canSeeRecord(record, user) && (matchesUser(user, record.getName())
                || matchesUser(user, record.getEmployeeIdHoNumber())
                || matchesUser(user, record.getGembaKaizenProviderName())
                || matchesUser(user, record.getAssignedTo())
                || matchesUser(user, record.getReassignedTo1())
                || matchesUser(user, record.getReassignedTo2()));
    }

    private boolean matchesScope(AppUser user, String department, String location) {
        if (user == null) {
            return false;
        }
        String expectedDepartment = compact(department);
        String expectedLocation = compact(location);
        boolean departmentMatches = expectedDepartment.isBlank() || expectedDepartment.equals(compact(user.getDepartment()));
        boolean locationMatches = expectedLocation.isBlank() || matchesAnyArea(user.getArea(), expectedLocation);
        return departmentMatches && locationMatches;
    }

    private boolean matchesAnyArea(String userAreas, String expectedLocation) {
        return List.of(trim(userAreas).split(",")).stream()
                .map(this::compact)
                .anyMatch(expectedLocation::equals);
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

    private String deriveDepartment(String location, String fallback, String userDepartment) {
        String locationText = trim(location);
        if (!locationText.isBlank()) {
            Optional<String> byArea = plantMasterDataService.list(PlantMasterDataService.PROCESS_AREA).stream()
                    .filter(item -> trim(item.getName()).equalsIgnoreCase(locationText))
                    .map(item -> trim(item.getParentDepartment()))
                    .filter(value -> !value.isBlank())
                    .findFirst();
            if (byArea.isPresent()) {
                return byArea.get();
            }
        }
        return firstNonBlank(fallback, userDepartment);
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
                "employeeId", trim(user.getEmployeeId()),
                "department", trim(user.getDepartment()),
                "area", trim(user.getArea()),
                "role", RoleAccess.normalize(user.getRole()),
                "designation", trim(user.getDesignation())
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
                .append(headerCell("Department"))
                .append(headerCell("Classification of Kaizen"))
                .append(headerCell("Gemba Kaizen Location"))
                .append(headerCell("Gemba Kaizen Generation Date"))
                .append(headerCell("Is Kaizen Implemented"))
                .append("</tr>");

        for (GembaKaizenRecord row : rows) {
            html.append("<tr>")
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

    private String compact(String value) {
        return trim(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private String compactUser(String value) {
        return trim(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9@.]", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
