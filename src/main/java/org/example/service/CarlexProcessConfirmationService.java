package org.example.service;

import org.example.entity.AppUser;
import org.example.entity.CarlexProcessConfirmation;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class CarlexProcessConfirmationService {
    private static final Logger log = LoggerFactory.getLogger(CarlexProcessConfirmationService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final CarlexProcessConfirmationRepository repository;
    private final AssignmentHistoryService assignmentHistoryService;
    private final AppUserRepository appUserRepository;
    private final EmailConfigService emailConfigService;

    public CarlexProcessConfirmationService(CarlexProcessConfirmationRepository repository,
                                            AssignmentHistoryService assignmentHistoryService,
                                            AppUserRepository appUserRepository,
                                            EmailConfigService emailConfigService) {
        this.repository = repository;
        this.assignmentHistoryService = assignmentHistoryService;
        this.appUserRepository = appUserRepository;
        this.emailConfigService = emailConfigService;
    }

    public List<CarlexProcessConfirmation> list() {
        return repository.findAllByOrderByDateOfGwProcessConfirmationConductedDescIdDesc();
    }

    public Optional<CarlexProcessConfirmation> get(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public CarlexProcessConfirmation create(CarlexProcessConfirmation record, String username) {
        LocalDateTime now = LocalDateTime.now();
        if (record.startTime == null) record.startTime = LocalTime.now();
        if (record.completionTime == null) record.completionTime = LocalTime.now();
        record.lastModifiedTime = now;
        applyUserIdentity(record, username);
        if (record.dateOfGwProcessConfirmationConducted == null) record.dateOfGwProcessConfirmationConducted = LocalDate.now();
        CarlexProcessConfirmation saved = repository.save(record);
        recordAssignmentHistory("", saved.assignedTo, record.assignmentRemark, username, saved);
        notifyAssignment("", saved.assignedTo, saved, false);
        if (isCompleted(saved)) {
            notifyCompletion(saved);
        }
        return saved;
    }

    @Transactional
    public Optional<CarlexProcessConfirmation> update(Long id, CarlexProcessConfirmation incoming, String username) {
        return repository.findById(id).map(existing -> {
            String previousAssignee = existing.assignedTo;
            boolean wasCompleted = isCompleted(existing);
            incoming.email = existing.email;
            incoming.name = existing.name;
            incoming.id = id;
            incoming.lastModifiedTime = LocalDateTime.now();
            CarlexProcessConfirmation saved = repository.save(incoming);
            recordAssignmentHistory(previousAssignee, saved.assignedTo, incoming.assignmentRemark, username, saved);
            notifyAssignment(previousAssignee, saved.assignedTo, saved, true);
            if (!wasCompleted && isCompleted(saved)) {
                notifyCompletion(saved);
            }
            return saved;
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }

    private void applyUserIdentity(CarlexProcessConfirmation record, String username) {
        appUserRepository.findByUsernameIgnoreCase(username).ifPresentOrElse(user -> {
            record.name = firstNonBlank(user.getName(), user.getUsername());
            record.email = trim(user.getEmail());
        }, () -> {
            record.name = trim(username);
            record.email = trim(record.email);
        });
    }

    private void recordAssignmentHistory(String oldAssignee, String newAssignee, String remarks,
                                         String username, CarlexProcessConfirmation record) {
        if (!canRecordAssignment(oldAssignee, newAssignee, username, record.areaOfGwProcessConfirmationConducted)) {
            return;
        }
        try {
            assignmentHistoryService.record(
                    "carlex-process-confirmation",
                    record.id,
                    oldAssignee,
                    newAssignee,
                    remarks,
                    username,
                    record.areaOfGwProcessConfirmationConducted
            );
        } catch (IllegalArgumentException ignored) {
            // Saving the confirmation record should not be rolled back by assignment-history validation.
        }
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

        String subject = (reassignment ? "CarlEX Process Confirmation Reassigned: #" : "CarlEX Process Confirmation Assigned: #")
                + record.id;
        sendEmail(List.of(recipient.get().getEmail()), subject,
                buildEmailBody(
                        reassignment ? "CarlEX Process Confirmation Reassigned" : "CarlEX Process Confirmation Assigned",
                        "A CarlEX Process Confirmation record has been assigned to you.",
                        record
                ));
    }

    private void notifyCompletion(CarlexProcessConfirmation record) {
        List<String> recipients = new ArrayList<>();
        resolveUser(record.assignedTo)
                .map(AppUser::getEmail)
                .filter(email -> !trim(email).isBlank())
                .ifPresent(recipients::add);
        if (!trim(record.email).isBlank() && !recipients.contains(record.email)) {
            recipients.add(record.email);
        }
        if (recipients.isEmpty()) {
            log.warn("CarlEX PC completion email not sent for recordId={} because no recipients were available", record.id);
            return;
        }
        sendEmail(recipients, "CarlEX Process Confirmation Completed: #" + record.id,
                buildEmailBody(
                        "CarlEX Process Confirmation Completed",
                        "All recorded observations have been marked complete.",
                        record
                ));
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
        List<String> statuses = List.of(
                trim(record.zm1Status),
                trim(record.zm2Status),
                trim(record.pm1Status),
                trim(record.pm2Status),
                trim(record.om1Status),
                trim(record.qm1Status),
                trim(record.qm2Status)
        ).stream().filter(status -> !status.isBlank()).toList();
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
                .append(row("Name", record.name))
                .append(row("Area", record.areaOfGwProcessConfirmationConducted))
                .append(row("Area Responsibility", record.areaResponsibility))
                .append(row("Assigned To", record.assignedTo))
                .append(row("GW PC Week", record.gwPcWeek))
                .append(row("ZM 1", record.zm1Description))
                .append(row("PM 1", record.pm1Description))
                .append(row("OM 1", record.om1Description))
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
                + " | OM: " + firstNonBlank(record.om1Status, "-")
                + " | QM: " + firstNonBlank(record.qm1Status, "-");
    }

    private String row(String label, String value) {
        return "<tr><td style='padding:8px 10px;background:#f9fafb;border:1px solid #e5e7eb;width:190px;color:#374151;font-weight:600;'>"
                + escape(label)
                + "</td><td style='padding:8px 10px;border:1px solid #e5e7eb;color:#111827;'>"
                + escape(firstNonBlank(value, "-"))
                + "</td></tr>";
    }

    private String escape(String value) {
        return trim(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private boolean canRecordAssignment(String oldAssignee, String newAssignee, String actorUsername, String scope) {
        String from = trim(oldAssignee);
        String to = trim(newAssignee);
        if (to.isBlank() || from.equalsIgnoreCase(to)) {
            return false;
        }
        Optional<AppUser> target = resolveUser(to);
        if (target.isEmpty() || !isActive(target.get()) || !matchesScope(target.get(), scope)) {
            return false;
        }
        if (from.isBlank()) {
            return isHod(target.get());
        }
        Optional<AppUser> actor = resolveUser(actorUsername);
        return actor.isPresent() && isHod(actor.get()) && isOperational(target.get());
    }

    private Optional<AppUser> resolveUser(String value) {
        String text = trim(value);
        if (text.isBlank()) {
            return Optional.empty();
        }
        Optional<AppUser> byUsername = appUserRepository.findByUsernameIgnoreCase(text);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        return appUserRepository.findAll().stream()
                .filter(user -> text.equalsIgnoreCase(trim(user.getName())))
                .findFirst();
    }

    private boolean isActive(AppUser user) {
        return user != null && !"INACTIVE".equalsIgnoreCase(trim(user.getStatus()));
    }

    private boolean isHod(AppUser user) {
        String designation = trim(user == null ? "" : user.getDesignation()).toUpperCase(Locale.ENGLISH);
        return user != null && (RoleAccess.isHod(user.getRole())
                || designation.contains("HOD")
                || designation.contains("HEAD_OF_DEPARTMENT"));
    }

    private boolean isOperational(AppUser user) {
        String role = RoleAccess.normalize(user == null ? "" : user.getRole());
        String designation = trim(user == null ? "" : user.getDesignation()).toUpperCase(Locale.ENGLISH);
        return "ENGINEER".equals(role)
                || "EXECUTIVE".equals(role)
                || "OPERATOR".equals(role)
                || "ENGINEER".equals(designation)
                || "EXECUTIVE".equals(designation)
                || "OPERATOR".equals(designation);
    }

    private boolean matchesScope(AppUser user, String scope) {
        String expected = trim(scope).toLowerCase(Locale.ENGLISH);
        if (expected.isBlank()) {
            return true;
        }
        return expected.equals(trim(user.getArea()).toLowerCase(Locale.ENGLISH))
                || expected.equals(trim(user.getDepartment()).toLowerCase(Locale.ENGLISH));
    }

    private String firstNonBlank(String first, String second) {
        String trimmedFirst = trim(first);
        return trimmedFirst.isBlank() ? trim(second) : trimmedFirst;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
