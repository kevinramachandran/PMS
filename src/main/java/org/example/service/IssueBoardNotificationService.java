package org.example.service;

import org.example.entity.AppUser;
import org.example.entity.IssueBoardItem;
import org.example.repository.AppUserRepository;
import org.example.repository.IssueBoardItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class IssueBoardNotificationService {

    private static final Logger log = LoggerFactory.getLogger(IssueBoardNotificationService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final IssueBoardItemRepository issueBoardItemRepository;
    private final AppUserRepository appUserRepository;
    private final EmailConfigService emailConfigService;
    private final ZoneId zoneId;

    public IssueBoardNotificationService(IssueBoardItemRepository issueBoardItemRepository,
                                         AppUserRepository appUserRepository,
                                         EmailConfigService emailConfigService,
                                         @Value("${app.timezone:UTC}") String timeZone) {
        this.issueBoardItemRepository = issueBoardItemRepository;
        this.appUserRepository = appUserRepository;
        this.emailConfigService = emailConfigService;
        this.zoneId = ZoneId.of(timeZone);
    }

    public void sendAssignmentNotifications(LocalDate boardDate,
                                            Map<Integer, IssueBoardItem> previousItems,
                                            Map<Integer, IssueBoardItem> currentItems) {
        for (Map.Entry<Integer, IssueBoardItem> entry : currentItems.entrySet()) {
            IssueBoardItem currentItem = entry.getValue();
            IssueBoardItem previousItem = previousItems.get(entry.getKey());

            if (!shouldSendAssignment(previousItem, currentItem)) {
                log.debug("Row {}: no assignment change — skipping notification", entry.getKey());
                continue;
            }

            log.info("Row {}: assignment changed, responsible='{}', problem='{}'",
                    entry.getKey(), currentItem.getResponsible(), currentItem.getProblem());

            Optional<AppUser> maybeRecipient = resolveRecipient(currentItem.getResponsible());
            if (maybeRecipient.isEmpty()) {
                log.warn("Row {}: no matching user found for responsible='{}' — assignment email NOT sent",
                        entry.getKey(), currentItem.getResponsible());
                continue;
            }

            AppUser recipient = maybeRecipient.get();
            String recipientEmail = recipient.getEmail();
            if (recipientEmail == null || recipientEmail.isBlank()) {
                log.warn("Row {}: user '{}' has no email address — assignment email NOT sent",
                        entry.getKey(), recipient.getUsername());
                continue;
            }

            log.info("Row {}: sending assignment email to '{}' ({})",
                    entry.getKey(), recipient.getUsername(), recipientEmail);

            String subject = "Issue Assigned: " + defaultText(currentItem.getProblem(), "Issue Board Item");
            String body = buildAssignmentBody(boardDate, recipient, currentItem);
            boolean sent = emailConfigService.sendEmail(List.of(recipientEmail), subject, body, true, true);
            if (sent) {
                log.info("Row {}: assignment email sent successfully to '{}'", entry.getKey(), recipientEmail);
            } else {
                log.warn("Row {}: assignment email was NOT sent to '{}' — check email configuration (enabled flag, SMTP password)", entry.getKey(), recipientEmail);
            }
        }
    }

    /**
     * Runs daily (default 08:00 app timezone).
     * Scans ALL open issues across every board date so that:
     *  - items due tomorrow receive a "due tomorrow" reminder
     *  - items whose target date has already passed receive an overdue alert
     *    (kept sending every day until the item is closed)
     */
    @Scheduled(cron = "${app.issue-board.reminder-cron:0 0 8 * * *}", zone = "${app.timezone:UTC}")
    public void sendPendingIssueReminders() {
        LocalDate today = LocalDate.now(zoneId);
        List<IssueBoardItem> openItems = issueBoardItemRepository.findAllOpenItemsWithTargetDate();

        for (IssueBoardItem item : openItems) {
            LocalDate targetDate = item.getTargetDate();

            Optional<AppUser> maybeRecipient = resolveRecipient(item.getResponsible());
            if (maybeRecipient.isEmpty()) {
                continue;
            }

            AppUser recipient = maybeRecipient.get();
            LocalDate boardDate = item.getBoardDate();

            if (targetDate.minusDays(1).equals(today)) {
                // Exactly 1 day before target date
                emailConfigService.sendEmail(
                        List.of(recipient.getEmail()),
                        "Reminder: Issue due tomorrow - " + defaultText(item.getProblem(), "Issue Board Item"),
                    buildReminderBody(boardDate, recipient, item, false, today),
                    true,
                    true
                );
            } else if (targetDate.isBefore(today)) {
                // Target date already passed and still open — keep alerting daily
                emailConfigService.sendEmail(
                        List.of(recipient.getEmail()),
                        "Overdue Issue Alert: " + defaultText(item.getProblem(), "Issue Board Item"),
                    buildReminderBody(boardDate, recipient, item, true, today),
                    true,
                    true
                );
            }
        }
    }

    private boolean shouldSendAssignment(IssueBoardItem previousItem, IssueBoardItem currentItem) {
        if (currentItem == null || isClosed(currentItem)) {
            return false;
        }

        String responsible = normalize(currentItem.getResponsible());
        if (responsible == null) {
            return false;
        }

        if (previousItem == null) {
            return true;
        }

        return !Objects.equals(normalize(previousItem.getResponsible()), responsible)
                || !Objects.equals(trim(previousItem.getProblem()), trim(currentItem.getProblem()))
                || !Objects.equals(trim(previousItem.getActions()), trim(currentItem.getActions()))
                || !Objects.equals(previousItem.getTargetDate(), currentItem.getTargetDate());
    }

    private Optional<AppUser> resolveRecipient(String responsible) {
        String normalized = normalize(responsible);
        if (normalized == null) {
            return Optional.empty();
        }

        Optional<AppUser> byUsername = appUserRepository.findByUsernameIgnoreCase(normalized);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        return appUserRepository.findByEmailIgnoreCase(normalized);
    }

    private boolean isClosed(IssueBoardItem item) {
        if (item == null) {
            return true;
        }

        String status = normalize(item.getStatus());
        return "100%".equals(status)
                || "closed".equals(status)
                || item.getCompletedDate() != null;
    }

    private String buildAssignmentBody(LocalDate boardDate, AppUser recipient, IssueBoardItem item) {
        String intro = "An issue has been assigned to you in Brewery PMS.";
        String footer = "Please review and update the issue status in PMS.";
        return buildIssueEmailHtml(
            recipient,
            item,
            boardDate,
            intro,
            footer,
            null
        );
    }

    private String buildReminderBody(LocalDate boardDate,
                                     AppUser recipient,
                                     IssueBoardItem item,
                                     boolean overdue,
                                     LocalDate today) {
        long overdueDays = overdue ? ChronoUnit.DAYS.between(item.getTargetDate(), today) : 0;
        String intro = overdue
            ? "This is an overdue reminder for an open issue assigned to you."
            : "This is a reminder that one of your assigned issues is due tomorrow.";
        String footer = "Please update the issue in PMS once it is completed.";
        String overdueDetail = overdue ? String.valueOf(overdueDays) + " day(s)" : null;

        return buildIssueEmailHtml(
            recipient,
            item,
            boardDate,
            intro,
            footer,
            overdueDetail
        );
    }

        private String buildIssueEmailHtml(AppUser recipient,
                           IssueBoardItem item,
                           LocalDate boardDate,
                           String intro,
                           String footer,
                           String overdueDetail) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='margin:0;padding:0;background:#f5f7f9;font-family:Arial,sans-serif;color:#1f2937;'>")
            .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='100%' style='background:#f5f7f9;padding:24px 0;'>")
            .append("<tr><td align='center'>")
            .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='680' style='max-width:680px;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;'>")
            .append("<tr><td style='background:#003d24;padding:16px 20px;'>")
            .append("<img src='cid:brandLogo' alt='Carlsberg logo' style='height:34px;width:auto;display:block;'>")
            .append("</td></tr>")
            .append("<tr><td style='padding:20px;'>")
            .append("<h2 style='margin:0 0 12px 0;color:#003d24;font-size:20px;'>Brewery PMS Issue Notification</h2>")
            .append("<p style='margin:0 0 8px 0;font-size:14px;'>Hello ")
            .append(escapeHtml(defaultText(recipient.getUsername(), "User")))
            .append(",</p>")
            .append("<p style='margin:0 0 16px 0;font-size:14px;'>")
            .append(escapeHtml(defaultText(intro, "")))
            .append("</p>")
            .append("<table role='presentation' cellspacing='0' cellpadding='0' border='0' width='100%' style='border-collapse:collapse;font-size:14px;'>")
            .append(buildDetailRow("Issue", defaultText(item.getProblem(), "-")))
            .append(buildDetailRow("Actions", defaultText(item.getActions(), "-")))
            .append(buildDetailRow("Target Date", formatDate(item.getTargetDate())))
            .append(buildDetailRow("Board Date", formatDate(boardDate)))
            .append(buildDetailRow("Current Status", defaultText(item.getStatus(), "0%")));

        if (overdueDetail != null) {
            html.append(buildDetailRow("Days Overdue", overdueDetail));
        }

        html.append(buildDetailRow("Remarks", defaultText(item.getRemarks(), "-")))
            .append("</table>")
            .append("<p style='margin:16px 0 0 0;font-size:14px;'>")
            .append(escapeHtml(defaultText(footer, "")))
            .append("</p>")
            .append("<p style='margin:16px 0 0 0;font-size:13px;color:#6b7280;'>Regards,<br>Brewery PMS</p>")
            .append("</td></tr>")
            .append("</table>")
            .append("</td></tr></table>")
            .append("</body></html>");

        return html.toString();
        }

        private String buildDetailRow(String label, String value) {
        return "<tr>"
            + "<td style='padding:8px 10px;background:#f9fafb;border:1px solid #e5e7eb;width:170px;color:#374151;font-weight:600;'>"
            + escapeHtml(label)
            + "</td>"
            + "<td style='padding:8px 10px;border:1px solid #e5e7eb;color:#111827;'>"
            + escapeHtml(defaultText(value, "-"))
            + "</td>"
            + "</tr>";
        }

        private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
        }

    private String formatDate(LocalDate value) {
        return value == null ? "-" : DATE_FORMATTER.format(value);
    }

    private String defaultText(String value, String fallback) {
        String trimmed = trim(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String normalize(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}