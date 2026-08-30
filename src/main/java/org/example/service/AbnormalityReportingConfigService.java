package org.example.service;

import org.example.entity.AbnormalityReportingRecord;
import org.example.entity.AppUser;
import org.example.repository.AbnormalityReportingRecordRepository;
import org.example.repository.AppUserRepository;
import org.springframework.stereotype.Service;

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

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Set<String> ASSIGNABLE_DESIGNATIONS = Set.of("ENGINEER", "EXECUTIVE", "OPERATOR");

    private final AbnormalityReportingRecordRepository repository;
    private final AbnormalityMasterDataService abnormalityMasterDataService;
    private final PlantMasterDataService plantMasterDataService;
    private final AppUserRepository appUserRepository;
    private final EmailConfigService emailConfigService;

    public AbnormalityReportingConfigService(AbnormalityReportingRecordRepository repository,
                                             AbnormalityMasterDataService abnormalityMasterDataService,
                                             PlantMasterDataService plantMasterDataService,
                                             AppUserRepository appUserRepository,
                                             EmailConfigService emailConfigService) {
        this.repository = repository;
        this.abnormalityMasterDataService = abnormalityMasterDataService;
        this.plantMasterDataService = plantMasterDataService;
        this.appUserRepository = appUserRepository;
        this.emailConfigService = emailConfigService;
    }

    public List<AbnormalityReportingRecord> list() {
        return repository.findAllByOrderByDateRaisedDescIdDesc();
    }

    public Optional<AbnormalityReportingRecord> find(Long id) {
        return id == null ? Optional.empty() : repository.findById(id);
    }

    public AbnormalityReportingRecord create(AbnormalityReportingRecord request, String username) {
        AbnormalityReportingRecord record = new AbnormalityReportingRecord();
        apply(record, request);
        if (isBlank(record.getTagRaisedBy())) {
            record.setTagRaisedBy(defaultText(username, ""));
        }
        applyClosedDate(record);
        AbnormalityReportingRecord saved = repository.save(record);
        notifyDepartmentHod(saved, "Abnormality Report Raised");
        return saved;
    }

    public Optional<AbnormalityReportingRecord> update(Long id, AbnormalityReportingRecord request, String username) {
        Optional<AbnormalityReportingRecord> existing = find(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        AbnormalityReportingRecord record = existing.get();
        boolean wasClosed = isClosed(record.getTagStatus());
        apply(record, request);
        applyClosedDate(record);
        AbnormalityReportingRecord saved = repository.save(record);
        if (!wasClosed && isClosed(saved.getTagStatus())) {
            notifyClosure(saved);
        }
        return Optional.of(saved);
    }

    public Map<String, Object> options() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("typeOfTags", abnormalityMasterDataService.names(AbnormalityMasterDataService.ABT_TAG_TYPE));
        payload.put("departments", plantMasterDataService.names(PlantMasterDataService.DEPARTMENT));
        payload.put("areaMachines", plantMasterDataService.names(PlantMasterDataService.PROCESS_AREA));
        payload.put("abnormalityDefectTypes", abnormalityMasterDataService.names(AbnormalityMasterDataService.ABNORMALITY_DEFECT_TYPE));
        payload.put("hods", userOptions(findDepartmentHods(null)));
        payload.put("assignableUsers", userOptions(findAssignableUsers(null)));
        return payload;
    }

    public Map<String, Object> departmentOptions(String department) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hods", userOptions(findDepartmentHods(department)));
        payload.put("assignableUsers", userOptions(findAssignableUsers(department)));
        return payload;
    }

    private void apply(AbnormalityReportingRecord record, AbnormalityReportingRecord request) {
        if (request == null) {
            throw new IllegalArgumentException("Record is required");
        }
        record.setTypeOfTag(trim(request.getTypeOfTag()));
        record.setPriority(trim(request.getPriority()));
        record.setAbnormalityTagNumber(trim(request.getAbnormalityTagNumber()));
        record.setTagRaisedBy(trim(request.getTagRaisedBy()));
        record.setDateRaised(request.getDateRaised());
        record.setShift(trim(request.getShift()));
        record.setAbnormalityRelatedTo(trim(request.getAbnormalityRelatedTo()));
        record.setDepartment(trim(request.getDepartment()));
        record.setAreaMachine(trim(request.getAreaMachine()));
        record.setComponent(trim(request.getComponent()));
        record.setDescription(trim(request.getDescription()));
        record.setProposedAction(trim(request.getProposedAction()));
        record.setPictureImage(trim(request.getPictureImage()));
        record.setAbnormalityDefectType(trim(request.getAbnormalityDefectType()));
        record.setAssignTo(trim(request.getAssignTo()));
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
        return appUserRepository.findAll().stream()
                .filter(this::isActive)
                .filter(user -> isBlank(department) || equalsIgnoreCase(user.getDepartment(), department))
                .filter(user -> normalize(user.getDesignation()).contains("HOD") || normalize(user.getDesignation()).contains("HEAD_OF_DEPARTMENT"))
                .toList();
    }

    private List<AppUser> findAssignableUsers(String department) {
        return appUserRepository.findAll().stream()
                .filter(this::isActive)
                .filter(user -> isBlank(department) || equalsIgnoreCase(user.getDepartment(), department))
                .filter(user -> ASSIGNABLE_DESIGNATIONS.contains(normalize(user.getDesignation())))
                .toList();
    }

    private List<Map<String, String>> userOptions(List<AppUser> users) {
        return users.stream()
                .map(user -> {
                    Map<String, String> option = new LinkedHashMap<>();
                    option.put("username", defaultText(user.getUsername(), ""));
                    option.put("name", defaultText(user.getName(), ""));
                    option.put("email", defaultText(user.getEmail(), ""));
                    option.put("department", defaultText(user.getDepartment(), ""));
                    option.put("designation", defaultText(user.getDesignation(), ""));
                    return option;
                })
                .toList();
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
                .append(row("Abnormality Tag Number", record.getAbnormalityTagNumber()))
                .append(row("Tag Raised By", record.getTagRaisedBy()))
                .append(row("Date Raised", formatDate(record.getDateRaised())))
                .append(row("Shift", record.getShift()))
                .append(row("Abnormality Related To", record.getAbnormalityRelatedTo()))
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
