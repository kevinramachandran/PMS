package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.AppUser;
import org.example.entity.CarlexProcessConfirmation;
import org.example.entity.CarlexProcessConfirmationObservation;
import org.example.repository.AppUserRepository;
import org.example.repository.CarlexProcessConfirmationRepository;
import org.example.util.RoleAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class CarlexProcessConfirmationService {
    private static final Logger log = LoggerFactory.getLogger(CarlexProcessConfirmationService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CarlexProcessConfirmationRepository repository;
    private final AssignmentHistoryService assignmentHistoryService;
    private final AppUserRepository appUserRepository;
    private final EmailConfigService emailConfigService;
    private final PlantMasterDataService plantMasterDataService;
    private final ObjectMapper objectMapper;

    public CarlexProcessConfirmationService(CarlexProcessConfirmationRepository repository,
                                            AssignmentHistoryService assignmentHistoryService,
                                            AppUserRepository appUserRepository,
                                            EmailConfigService emailConfigService,
                                            PlantMasterDataService plantMasterDataService,
                                            ObjectMapper objectMapper) {
        this.repository = repository;
        this.assignmentHistoryService = assignmentHistoryService;
        this.appUserRepository = appUserRepository;
        this.emailConfigService = emailConfigService;
        this.plantMasterDataService = plantMasterDataService;
        this.objectMapper = objectMapper;
    }

    public List<CarlexProcessConfirmation> list() {
        return repository.findAllByOrderByDateOfGwProcessConfirmationConductedDescIdDesc().stream()
                .peek(this::enrichDerivedFields)
                .toList();
    }

    public List<CarlexProcessConfirmation> listForUser(String username, String role) {
        if (RoleAccess.isAdmin(role)) {
            return list();
        }
        Optional<AppUser> current = currentUser(username);
        if (current.isEmpty()) {
            return List.of();
        }
        return list().stream()
                .filter(record -> canSeeRecord(record, current.get()))
                .toList();
    }

    public Optional<CarlexProcessConfirmation> get(Long id) {
        return repository.findById(id).map(this::enrichDerivedFields);
    }

    public Optional<CarlexProcessConfirmation> getForUser(Long id, String username, String role) {
        return repository.findById(id)
                .map(this::enrichDerivedFields)
                .filter(record -> RoleAccess.isAdmin(role)
                        || currentUser(username).filter(user -> canSeeRecord(record, user)).isPresent());
    }

    @Transactional
    public CarlexProcessConfirmation create(CarlexProcessConfirmation record, String username, String role) {
        applyDefaults(record, username, true);
        AppUser actor = currentUser(username).orElse(null);
        if (!RoleAccess.isAdmin(role) && !isHod(actor)) {
            throw new IllegalArgumentException("Only HoD users can create Process Confirmations");
        }
        validateConfigured(record.department, plantMasterDataService.names(PlantMasterDataService.DEPARTMENT), "Department");
        validateConfigured(record.areaOfGwProcessConfirmationConducted, plantMasterDataService.names(PlantMasterDataService.PROCESS_AREA), "Area");
        validateAssignedTo(record.assignedTo, record.department, record.areaOfGwProcessConfirmationConducted, actor, role, "", null);
        replaceDynamicObservations(record);
        validateRequiredObservations(record);
        CarlexProcessConfirmation saved = repository.save(record);
        assignmentHistoryService.record("carlex-process-confirmation", saved.id, "", saved.assignedTo,
                saved.assignmentRemark, username, assignmentScope(saved));
        notifyAssignment("", saved.assignedTo, saved, false);
        if (isCompleted(saved)) {
            notifyCompletion(saved);
        }
        return saved;
    }

    @Transactional
    public Optional<CarlexProcessConfirmation> update(Long id, CarlexProcessConfirmation incoming, String username, String role) {
        return getForUser(id, username, role).map(existing -> {
            AppUser actor = currentUser(username).orElse(null);
            if (!RoleAccess.isAdmin(role) && !canUpdateRecord(existing, actor)) {
                throw new IllegalArgumentException("You can update only Process Confirmations created by you, assigned to you, or within your permitted area");
            }
            String previousAssignee = existing.assignedTo;
            boolean wasCompleted = isCompleted(existing);
            copyEditableFields(existing, incoming);
            applyDefaults(existing, username, false);
            validateConfigured(existing.department, plantMasterDataService.names(PlantMasterDataService.DEPARTMENT), "Department");
            validateConfigured(existing.areaOfGwProcessConfirmationConducted, plantMasterDataService.names(PlantMasterDataService.PROCESS_AREA), "Area");
            validateAssignedTo(existing.assignedTo, existing.department, existing.areaOfGwProcessConfirmationConducted,
                    actor, role, previousAssignee, existing);
            replaceDynamicObservations(existing);
            validateRequiredObservations(existing);
            CarlexProcessConfirmation saved = repository.save(existing);
            assignmentHistoryService.record("carlex-process-confirmation", saved.id, previousAssignee, saved.assignedTo,
                    incoming.assignmentRemark, username, assignmentScope(saved));
            notifyAssignment(previousAssignee, saved.assignedTo, saved, true);
            if (!wasCompleted && isCompleted(saved)) {
                notifyCompletion(saved);
            }
            return saved;
        });
    }

    @Transactional
    public boolean delete(Long id, String username, String role) {
        Optional<CarlexProcessConfirmation> existing = getForUser(id, username, role);
        if (existing.isEmpty()) {
            return false;
        }
        AppUser actor = currentUser(username).orElse(null);
        if (!RoleAccess.isAdmin(role) && !canUpdateRecord(existing.get(), actor)) {
            throw new IllegalArgumentException("You can delete only records within your permitted Process Confirmation workflow");
        }
        repository.deleteById(id);
        return true;
    }

    public Map<String, Object> options(String username, String role, String department, String area, Long recordId) {
        Map<String, Object> options = new LinkedHashMap<>();
        Optional<AppUser> current = currentUser(username);
        options.put("currentUser", current.map(this::userOption).orElse(Map.of()));
        options.put("plants", plantMasterDataService.names(PlantMasterDataService.PLANT));
        options.put("plantItems", plantMasterDataService.list(PlantMasterDataService.PLANT));
        options.put("departments", plantMasterDataService.names(PlantMasterDataService.DEPARTMENT));
        options.put("departmentItems", plantMasterDataService.list(PlantMasterDataService.DEPARTMENT));
        options.put("areas", plantMasterDataService.names(PlantMasterDataService.PROCESS_AREA));
        options.put("areaItems", plantMasterDataService.list(PlantMasterDataService.PROCESS_AREA));
        CarlexProcessConfirmation record = recordId == null ? null : repository.findById(recordId).orElse(null);
        String resolvedArea = record == null ? area : record.areaOfGwProcessConfirmationConducted;
        String resolvedDepartment = record == null
                ? deriveDepartment(area, department, current.map(AppUser::getDepartment).orElse(""))
                : record.department;
        options.put("assignmentUsers", assignmentUsers(resolvedDepartment, resolvedArea, current.orElse(null), role, record));
        options.put("defaultAssignedTo", defaultDepartmentHod(resolvedDepartment, resolvedArea)
                .map(user -> firstNonBlank(user.getUsername(), user.getName())).orElse(""));
        return options;
    }

    private void copyEditableFields(CarlexProcessConfirmation target, CarlexProcessConfirmation source) {
        target.startTime = source.startTime;
        target.completionTime = source.completionTime;
        target.dateOfGwProcessConfirmationConducted = source.dateOfGwProcessConfirmationConducted;
        target.gwPcWeek = trim(source.gwPcWeek);
        target.department = trim(source.department);
        target.areaOfGwProcessConfirmationConducted = trim(source.areaOfGwProcessConfirmationConducted);
        target.areaResponsibility = trim(source.areaResponsibility);
        target.assignedTo = trim(source.assignedTo);
        target.assignmentRemark = trim(source.assignmentRemark);
        target.zm1Description = trim(source.zm1Description);
        target.zm1CounterMeasureActions = trim(source.zm1CounterMeasureActions);
        target.zm1Status = trim(source.zm1Status);
        target.zm1ObservationImage = trim(source.zm1ObservationImage);
        target.anotherZmObservation = source.anotherZmObservation;
        target.zm2Description = trim(source.zm2Description);
        target.zm2CounterMeasureActions = trim(source.zm2CounterMeasureActions);
        target.zm2Status = trim(source.zm2Status);
        target.zm2ObservationImage = trim(source.zm2ObservationImage);
        target.pm1Description = trim(source.pm1Description);
        target.pm1CounterMeasureActions = trim(source.pm1CounterMeasureActions);
        target.pm1Status = trim(source.pm1Status);
        target.pm1ObservationImage = trim(source.pm1ObservationImage);
        target.anotherPmObservation = source.anotherPmObservation;
        target.pm2Description = trim(source.pm2Description);
        target.pm2CounterMeasureActions = trim(source.pm2CounterMeasureActions);
        target.pm2Status = trim(source.pm2Status);
        target.pm2ObservationImage = trim(source.pm2ObservationImage);
        target.qm1Description = trim(source.qm1Description);
        target.qm1CounterMeasureActions = trim(source.qm1CounterMeasureActions);
        target.qm1Status = trim(source.qm1Status);
        target.qm1ObservationImage = trim(source.qm1ObservationImage);
        target.anotherQmObservation = source.anotherQmObservation;
        target.qm2Description = trim(source.qm2Description);
        target.qm2CounterMeasureActions = trim(source.qm2CounterMeasureActions);
        target.qm2Status = trim(source.qm2Status);
        target.qm2ObservationImage = trim(source.qm2ObservationImage);
    }

    private CarlexProcessConfirmation enrichDerivedFields(CarlexProcessConfirmation record) {
        if (record == null) {
            return null;
        }
        record.department = deriveDepartment(record.areaOfGwProcessConfirmationConducted, record.department, "");
        record.zmObservationsJson = observationsJson(record, "ZM");
        record.pmObservationsJson = observationsJson(record, "PM");
        record.qmObservationsJson = observationsJson(record, "QM");
        return record;
    }

    private void replaceDynamicObservations(CarlexProcessConfirmation record) {
        if (record.observations == null) {
            record.observations = new ArrayList<>();
        }
        if (isBlank(record.zmObservationsJson) && isBlank(record.pmObservationsJson) && isBlank(record.qmObservationsJson)) {
            return;
        }
        record.observations.clear();
        appendObservationRows(record, "ZM", record.zmObservationsJson);
        appendObservationRows(record, "PM", record.pmObservationsJson);
        appendObservationRows(record, "QM", record.qmObservationsJson);
    }

    private void appendObservationRows(CarlexProcessConfirmation record, String groupType, String json) {
        List<CarlexProcessConfirmationObservation> rows = parseObservationRows(groupType, json);
        for (CarlexProcessConfirmationObservation row : rows) {
            row.setConfirmation(record);
            record.observations.add(row);
        }
    }

    private List<CarlexProcessConfirmationObservation> parseObservationRows(String groupType, String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            List<CarlexProcessConfirmationObservation> rows = new ArrayList<>();
            int order = 1;
            for (JsonNode node : root) {
                String description = trim(node.path("description").asText(""));
                String actions = trim(node.path("counterMeasureActions").asText(""));
                String status = trim(node.path("status").asText(""));
                String image = trim(node.path("observationImage").asText(""));
                if (description.isBlank() && actions.isBlank() && status.isBlank() && image.isBlank()) {
                    continue;
                }
                if (description.isBlank() || actions.isBlank() || status.isBlank()) {
                    throw new IllegalArgumentException(groupType + " " + order + " must include description, counter measure actions, and status");
                }
                if (!Set.of("P", "D", "C", "A").contains(status.toUpperCase(Locale.ENGLISH))) {
                    throw new IllegalArgumentException(groupType + " " + order + " status must be P, D, C, or A");
                }
                CarlexProcessConfirmationObservation row = new CarlexProcessConfirmationObservation();
                row.setGroupType(groupType);
                row.setObservationOrder(order++);
                row.setDescription(description);
                row.setCounterMeasureActions(actions);
                row.setStatus(status);
                row.setObservationImage(image);
                rows.add(row);
            }
            return rows;
        } catch (Exception ex) {
            throw new IllegalArgumentException(groupType + " observations are not valid");
        }
    }

    private String observationsJson(CarlexProcessConfirmation record, String groupType) {
        if (record.observations == null || record.observations.isEmpty()) {
            return "";
        }
        List<Map<String, String>> rows = record.observations.stream()
                .filter(row -> groupType.equalsIgnoreCase(trim(row.getGroupType())))
                .sorted((left, right) -> Integer.compare(
                        left.getObservationOrder() == null ? 0 : left.getObservationOrder(),
                        right.getObservationOrder() == null ? 0 : right.getObservationOrder()))
                .map(row -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("description", trim(row.getDescription()));
                    item.put("counterMeasureActions", trim(row.getCounterMeasureActions()));
                    item.put("status", trim(row.getStatus()));
                    item.put("observationImage", trim(row.getObservationImage()));
                    return item;
                })
                .toList();
        if (rows.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception ex) {
            return "";
        }
    }

    private void applyDefaults(CarlexProcessConfirmation record, String username, boolean forceUserIdentity) {
        Optional<AppUser> current = currentUser(username);
        current.ifPresent(user -> {
            if (forceUserIdentity || isBlank(record.name)) {
                record.name = firstNonBlank(user.getName(), user.getUsername());
            }
            if (forceUserIdentity || isBlank(record.email)) {
                record.email = trim(user.getEmail());
            }
            if (forceUserIdentity || isBlank(record.processConfirmationDoneBy)) {
                record.processConfirmationDoneBy = firstNonBlank(user.getName(), user.getUsername());
            }
        });
        if (isBlank(record.name)) {
            record.name = trim(username);
        }
        if (isBlank(record.processConfirmationDoneBy)) {
            record.processConfirmationDoneBy = record.name;
        }
        if (record.startTime == null) record.startTime = LocalTime.now();
        if (record.completionTime == null) record.completionTime = LocalTime.now();
        record.lastModifiedTime = LocalDateTime.now();
        if (record.dateOfGwProcessConfirmationConducted == null) record.dateOfGwProcessConfirmationConducted = LocalDate.now();
        record.department = deriveDepartment(record.areaOfGwProcessConfirmationConducted, record.department,
                current.map(AppUser::getDepartment).orElse(""));
        if (isBlank(record.areaResponsibility)) {
            defaultDepartmentHod(record.department, record.areaOfGwProcessConfirmationConducted)
                    .ifPresent(user -> record.areaResponsibility = firstNonBlank(user.getUsername(), user.getName()));
        }
        if (isBlank(record.assignedTo)) {
            defaultDepartmentHod(record.department, record.areaOfGwProcessConfirmationConducted)
                    .ifPresent(user -> record.assignedTo = firstNonBlank(user.getUsername(), user.getName()));
        }
    }

    private void validateRequiredObservations(CarlexProcessConfirmation record) {
        boolean hasDynamicRows = record.observations != null && !record.observations.isEmpty();
        boolean hasLegacyRows = List.of(trim(record.zm1Description), trim(record.zm2Description),
                        trim(record.pm1Description), trim(record.pm2Description),
                        trim(record.qm1Description), trim(record.qm2Description))
                .stream()
                .anyMatch(value -> !value.isBlank());
        if (!hasDynamicRows && !hasLegacyRows) {
            throw new IllegalArgumentException("Enter at least one ZM, PM, or QM observation before saving");
        }
    }

    private void validateAssignedTo(String value, String department, String area, AppUser actor, String role,
                                    String previousAssignee, CarlexProcessConfirmation record) {
        String trimmed = trim(value);
        if (trimmed.isBlank()) {
            return;
        }
        boolean configured = assignmentUsers(department, area, actor, role, record).stream()
                .anyMatch(user -> trim(user.get("username")).equalsIgnoreCase(trimmed)
                        || trim(user.get("label")).equalsIgnoreCase(trimmed));
        if (!configured) {
            throw new IllegalArgumentException("Assigned To must be a permitted workflow user");
        }
    }

    private List<Map<String, String>> assignmentUsers(String department, String area, AppUser actor, String role,
                                                      CarlexProcessConfirmation record) {
        List<AppUser> scoped = activeUsers().stream()
                .filter(user -> matchesAssignmentScope(user, department, area))
                .toList();
        boolean hasScope = !isBlank(department) || !isBlank(area);
        List<AppUser> pool = hasScope ? scoped : activeUsers();
        if (RoleAccess.isAdmin(role)) {
            return pool.stream().map(this::userOption).toList();
        }
        boolean existingAssignment = record != null && !isBlank(record.assignedTo);
        boolean actorIsAreaHod = isStrictAreaHod(actor);
        return pool.stream()
                .filter(user -> {
                    if (!existingAssignment) {
                        return isAreaHod(user);
                    }
                    if (actorIsAreaHod) {
                        return isOperational(user);
                    }
                    if (isHod(actor)) {
                        return isAreaHod(user);
                    }
                    return isOperational(user);
                })
                .map(this::userOption)
                .toList();
    }

    private Optional<AppUser> defaultDepartmentHod(String department, String area) {
        return activeUsers().stream()
                .filter(this::isAreaHod)
                .filter(user -> matchesAssignmentScope(user, department, area))
                .findFirst();
    }

    private boolean canSeeRecord(CarlexProcessConfirmation record, AppUser user) {
        if (record == null || user == null) {
            return false;
        }
        if (matchesUser(user, record.name) || matchesUser(user, record.email)
                || matchesUser(user, record.processConfirmationDoneBy) || matchesUser(user, record.assignedTo)
                || matchesUser(user, record.areaResponsibility)) {
            return true;
        }
        return false;
    }

    private boolean canUpdateRecord(CarlexProcessConfirmation record, AppUser user) {
        return canSeeRecord(record, user);
    }

    private String deriveDepartment(String area, String fallback, String userDepartment) {
        String areaText = trim(area);
        if (!areaText.isBlank()) {
            Optional<String> byArea = plantMasterDataService.list(PlantMasterDataService.PROCESS_AREA).stream()
                    .filter(item -> trim(item.getName()).equalsIgnoreCase(areaText))
                    .map(item -> trim(item.getParentDepartment()))
                    .filter(value -> !value.isBlank())
                    .findFirst();
            if (byArea.isPresent()) {
                return byArea.get();
            }
        }
        return firstNonBlank(fallback, userDepartment);
    }

    private void validateConfigured(String value, List<String> options, String label) {
        String trimmed = trim(value);
        if (trimmed.isBlank()) {
            return;
        }
        boolean configured = options.stream().anyMatch(option -> trim(option).equalsIgnoreCase(trimmed));
        if (!configured) {
            throw new IllegalArgumentException(label + " must be configured in Master Data");
        }
    }

    private String assignmentScope(CarlexProcessConfirmation record) {
        return firstNonBlank(record.areaOfGwProcessConfirmationConducted, record.department);
    }

    private void notifyAssignment(String oldAssignee, String newAssignee, CarlexProcessConfirmation record, boolean reassignment) {
        String target = trim(newAssignee);
        if (target.isBlank() || trim(oldAssignee).equalsIgnoreCase(target)) {
            return;
        }
        Optional<AppUser> recipient = resolveUser(target);
        if (recipient.isEmpty() || !isActive(recipient.get()) || trim(recipient.get().getEmail()).isBlank()) {
            log.warn("CarlEX PC email not sent for recordId={} because assignedTo='{}' has no active user email", record.id, target);
            return;
        }
        String subject = (reassignment ? "CarlEX Process Confirmation Reassigned: #" : "CarlEX Process Confirmation Assigned: #") + record.id;
        sendEmail(List.of(recipient.get().getEmail()), subject,
                buildEmailBody(reassignment ? "CarlEX Process Confirmation Reassigned" : "CarlEX Process Confirmation Assigned",
                        "A CarlEX Process Confirmation record has been assigned to you.", record));
    }

    private void notifyCompletion(CarlexProcessConfirmation record) {
        List<String> recipients = new ArrayList<>();
        resolveUser(record.assignedTo).map(AppUser::getEmail).filter(email -> !isBlank(email)).ifPresent(recipients::add);
        if (!isBlank(record.email) && !recipients.contains(record.email)) {
            recipients.add(record.email);
        }
        if (recipients.isEmpty()) {
            log.warn("CarlEX PC completion email not sent for recordId={} because no recipients were available", record.id);
            return;
        }
        sendEmail(recipients, "CarlEX Process Confirmation Completed: #" + record.id,
                buildEmailBody("CarlEX Process Confirmation Completed", "All recorded observations have been marked complete.", record));
    }

    private void sendEmail(List<String> recipients, String subject, String body) {
        try {
            boolean sent = emailConfigService.sendEmail(recipients, subject, body, true, true);
            if (!sent) {
                log.warn("CarlEX PC email was not sent to {}. Check SMTP configuration and recipient emails.", recipients);
            }
        } catch (Exception ex) {
            log.error("Failed to send CarlEX PC email to {}", recipients, ex);
        }
    }

    private boolean isCompleted(CarlexProcessConfirmation record) {
        List<String> statuses = List.of(trim(record.zm1Status), trim(record.zm2Status),
                trim(record.pm1Status), trim(record.pm2Status), trim(record.qm1Status), trim(record.qm2Status))
                .stream().filter(status -> !status.isBlank()).toList();
        return !statuses.isEmpty() && statuses.stream().allMatch(status -> "A".equalsIgnoreCase(status));
    }

    private String buildEmailBody(String title, String intro, CarlexProcessConfirmation record) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='margin:0;padding:0;background:#f5f7f9;font-family:Arial,sans-serif;color:#1f2937;'>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='100%' style='background:#f5f7f9;padding:24px 0;'>")
                .append("<tr><td align='center'>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='680' style='max-width:680px;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;'>")
                .append("<tr><td style='background:#003d24;padding:16px 20px;'>")
                .append("<img src='cid:brandLogo' alt='Carlsberg logo' style='height:34px;width:auto;display:block;'>")
                .append("</td></tr><tr><td style='padding:20px;'>")
                .append("<h2 style='margin:0 0 8px;color:#003d24;font-size:20px;'>").append(escape(title)).append("</h2>")
                .append("<p style='margin:0 0 16px;font-size:14px;line-height:1.5;'>").append(escape(intro)).append("</p>")
                .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='100%' style='border-collapse:collapse;font-size:14px;'>")
                .append(row("Record ID", record.id == null ? "-" : "#" + record.id))
                .append(row("Date", record.dateOfGwProcessConfirmationConducted == null ? "-" : DATE_FORMATTER.format(record.dateOfGwProcessConfirmationConducted)))
                .append(row("Department", record.department))
                .append(row("Area", record.areaOfGwProcessConfirmationConducted))
                .append(row("Conducted By", record.name))
                .append(row("Email", record.email))
                .append(row("Area Responsibility", record.areaResponsibility))
                .append(row("Assigned To", record.assignedTo))
                .append(row("GW PC Week", record.gwPcWeek))
                .append(row("ZM 1", record.zm1Description))
                .append(row("PM 1", record.pm1Description))
                .append(row("QM 1", record.qm1Description))
                .append(row("Status", statusSummary(record)))
                .append("</table>")
                .append("<p style='margin:16px 0 0;font-size:13px;color:#6b7280;'>Regards,<br>Brewery PMS</p>")
                .append("</td></tr></table></td></tr></table></body></html>");
        return html.toString();
    }

    private String statusSummary(CarlexProcessConfirmation record) {
        return "ZM: " + firstNonBlank(record.zm1Status, "-")
                + " | PM: " + firstNonBlank(record.pm1Status, "-")
                + " | QM: " + firstNonBlank(record.qm1Status, "-");
    }

    private String row(String label, String value) {
        return "<tr><td style='padding:8px 10px;background:#f9fafb;border:1px solid #e5e7eb;width:190px;color:#374151;font-weight:600;'>"
                + escape(label)
                + "</td><td style='padding:8px 10px;border:1px solid #e5e7eb;color:#111827;'>"
                + escape(firstNonBlank(value, "-"))
                + "</td></tr>";
    }

    private Optional<AppUser> resolveUser(String value) {
        String text = trim(value);
        if (text.isBlank()) {
            return Optional.empty();
        }
        return appUserRepository.findByUsernameIgnoreCase(text)
                .or(() -> appUserRepository.findByEmailIgnoreCase(text))
                .or(() -> appUserRepository.findByNameIgnoreCase(text))
                .or(() -> appUserRepository.findByEmployeeIdIgnoreCase(text));
    }

    private Optional<AppUser> currentUser(String username) {
        if (isBlank(username)) {
            return Optional.empty();
        }
        return appUserRepository.findByUsernameIgnoreCase(username);
    }

    private List<AppUser> activeUsers() {
        return appUserRepository.findAll().stream().filter(this::isActive).toList();
    }

    private boolean isActive(AppUser user) {
        return user != null && !"INACTIVE".equalsIgnoreCase(trim(user.getStatus()));
    }

    private boolean isHod(AppUser user) {
        if (user == null) {
            return false;
        }
        String designation = trim(user.getDesignation()).toUpperCase(Locale.ENGLISH);
        return RoleAccess.isHod(user.getRole())
                || designation.contains("HOD")
                || designation.contains("HEAD_OF_DEPARTMENT")
                || designation.contains("AREA HEAD");
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

    private boolean isStrictAreaHod(AppUser user) {
        if (user == null) {
            return false;
        }
        String role = RoleAccess.normalize(user.getRole());
        String designation = trim(user.getDesignation()).toUpperCase(Locale.ENGLISH);
        return RoleAccess.AREA_HOD.equals(role)
                || designation.contains("AREA HOD")
                || designation.contains("AREA_HOD")
                || designation.contains("AREA HEAD");
    }

    private boolean isOperational(AppUser user) {
        if (user == null) {
            return false;
        }
        String role = RoleAccess.normalize(user.getRole());
        String designation = trim(user.getDesignation()).toUpperCase(Locale.ENGLISH).replace(' ', '_');
        return Set.of(RoleAccess.ENGINEER, RoleAccess.EXECUTIVE, RoleAccess.OPERATOR).contains(role)
                || Set.of(RoleAccess.ENGINEER, RoleAccess.EXECUTIVE, RoleAccess.OPERATOR).contains(designation);
    }

    private boolean matchesScope(AppUser user, String department, String area) {
        if (user == null) {
            return false;
        }
        String expectedDepartment = compact(department);
        String expectedArea = compact(area);
        boolean departmentMatches = expectedDepartment.isBlank() || expectedDepartment.equals(compact(user.getDepartment()));
        boolean areaMatches = expectedArea.isBlank() || matchesAnyArea(user.getArea(), expectedArea);
        return departmentMatches && areaMatches;
    }

    private boolean matchesAssignmentScope(AppUser user, String department, String area) {
        if (user == null) {
            return false;
        }
        String expectedDepartment = compact(department);
        if (!expectedDepartment.isBlank()) {
            return expectedDepartment.equals(compact(user.getDepartment()));
        }
        String expectedArea = compact(area);
        return expectedArea.isBlank() || matchesAnyArea(user.getArea(), expectedArea);
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

    private Map<String, String> userOption(AppUser user) {
        Map<String, String> option = new LinkedHashMap<>();
        option.put("username", firstNonBlank(user.getUsername(), user.getEmail()));
        option.put("label", firstNonBlank(user.getName(), user.getUsername()));
        option.put("email", trim(user.getEmail()));
        option.put("department", trim(user.getDepartment()));
        option.put("area", trim(user.getArea()));
        option.put("role", RoleAccess.normalize(user.getRole()));
        option.put("designation", trim(user.getDesignation()));
        return option;
    }

    private String escape(String value) {
        return trim(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String firstNonBlank(String first, String second) {
        String trimmedFirst = trim(first);
        return trimmedFirst.isBlank() ? trim(second) : trimmedFirst;
    }

    private boolean isBlank(String value) {
        return trim(value).isBlank();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String compact(String value) {
        return trim(value).toUpperCase(Locale.ENGLISH).replaceAll("[^A-Z0-9]", "");
    }

    private String compactUser(String value) {
        return trim(value).toUpperCase(Locale.ENGLISH).replaceAll("[^A-Z0-9@.]", "");
    }
}
