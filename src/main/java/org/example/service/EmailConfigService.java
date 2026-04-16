package org.example.service;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.example.entity.EmailConfig;
import org.example.model.EmailConfigPayload;
import org.example.model.EmailConfigResponse;
import org.example.model.EmailTestResponse;
import org.example.repository.EmailConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class EmailConfigService {

    private static final Long SINGLETON_ID = 1L;
    private static final String PASSWORD_MASK = "********";
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Logger log = LoggerFactory.getLogger(EmailConfigService.class);
    private static final String BRAND_LOGO_CLASSPATH = "static/images/carlsbergWhiteLogo.png";

    private final EmailConfigRepository repository;
    private final SensitiveConfigCryptoService cryptoService;
    private final long testRateLimitMs;
    private final ConcurrentHashMap<String, Long> testRequestTimes = new ConcurrentHashMap<>();

    public EmailConfigService(
            EmailConfigRepository repository,
            SensitiveConfigCryptoService cryptoService,
            @Value("${app.email-config.test-rate-limit-ms:5000}") long testRateLimitMs) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.testRateLimitMs = testRateLimitMs;
    }

    public EmailConfigResponse getConfiguration() {
        EmailConfigResponse response = new EmailConfigResponse();
        Optional<EmailConfig> existing = repository.findById(Objects.requireNonNull(SINGLETON_ID));
        if (existing.isEmpty()) {
            response.setPort(587);
            response.setEncryption("TLS");
            response.setEnabled(false);
            response.setPasswordConfigured(false);
            return response;
        }

        EmailConfig config = existing.get();
        response.setHost(config.getHost());
        response.setPort(config.getPort());
        response.setUsername(config.getUsername());
        response.setEncryption(config.getEncryption());
        response.setFromEmail(config.getFromEmail());
        response.setFromName(config.getFromName());
        response.setReplyTo(config.getReplyTo());
        response.setEnabled(config.isEnabled());
        response.setPasswordConfigured(config.getEncryptedPassword() != null && !config.getEncryptedPassword().isBlank());
        return response;
    }

    public void saveConfiguration(EmailConfigPayload payload) {
        ValidationResult validation = validatePayload(payload, true);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.message());
        }

        EmailConfig target = repository.findById(Objects.requireNonNull(SINGLETON_ID)).orElseGet(() -> {
            EmailConfig config = new EmailConfig();
            config.setId(SINGLETON_ID);
            return config;
        });

        target.setHost(trim(payload.getHost()));
        target.setPort(payload.getPort());
        target.setUsername(trim(payload.getUsername()));
        target.setEncryption(normalizeEncryption(payload.getEncryption()));
        target.setFromEmail(trim(payload.getFromEmail()));
        target.setFromName(trim(payload.getFromName()));
        target.setReplyTo(trimToNull(payload.getReplyTo()));
        target.setEnabled(Boolean.TRUE.equals(payload.getEnabled()));

        String resolvedPassword = resolvePassword(payload.getPassword(), target);
        if (resolvedPassword == null || resolvedPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        target.setEncryptedPassword(cryptoService.encrypt(resolvedPassword));
        repository.save(target);
    }

    public EmailTestResponse testConfiguration(EmailConfigPayload payload, String actorKey) {
        ValidationResult validation = validatePayload(payload, false);
        if (!validation.isValid()) {
            return new EmailTestResponse("ERROR", validation.message());
        }

        if (isRateLimited(actorKey)) {
            return new EmailTestResponse("ERROR", "Please wait a few seconds before testing again");
        }

        String password = resolvePassword(payload.getPassword(), repository.findById(Objects.requireNonNull(SINGLETON_ID)).orElse(null));
        if (password == null || password.isBlank()) {
            return new EmailTestResponse("ERROR", "Password is required");
        }

        JavaMailSenderImpl mailSender = buildMailSender(payload, password);
        String host = trim(payload.getHost());
        try {
            mailSender.testConnection();
            return new EmailTestResponse("SUCCESS", "Connection successful");
        } catch (MailAuthenticationException ex) {
            return new EmailTestResponse("ERROR", buildAuthFailedMessage(host));
        } catch (MailSendException ex) {
            return new EmailTestResponse("ERROR", resolveConnectionMessage(ex, host));
        } catch (MessagingException ex) {
            return new EmailTestResponse("ERROR", resolveConnectionMessage(ex, host));
        }
    }

    public boolean hasStoredPassword() {
        return repository.findById(Objects.requireNonNull(SINGLETON_ID))
                .map(config -> config.getEncryptedPassword() != null && !config.getEncryptedPassword().isBlank())
                .orElse(false);
    }

    public boolean sendEmail(List<String> recipients, String subject, String body) {
        return sendEmail(recipients, subject, body, false, false);
    }

    public boolean sendEmail(List<String> recipients,
                             String subject,
                             String body,
                             boolean isHtml,
                             boolean includeBrandLogo) {
        if (recipients == null || recipients.isEmpty()) {
            return false;
        }

        Optional<EmailConfig> maybeConfig = repository.findById(Objects.requireNonNull(SINGLETON_ID));
        if (maybeConfig.isEmpty()) {
            log.warn("Skipping email send because no email configuration is stored");
            return false;
        }

        EmailConfig config = maybeConfig.get();
        if (!config.isEnabled()) {
            log.warn("Skipping email send because email configuration is disabled — enable it on the Email Configuration page");
            return false;
        }

        String password = resolvePassword(PASSWORD_MASK, config);
        if (password == null || password.isBlank()) {
            log.warn("Skipping email send because no SMTP password is configured");
            return false;
        }

        JavaMailSenderImpl mailSender = buildMailSender(config, password);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, includeBrandLogo, StandardCharsets.UTF_8.name());
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(Objects.requireNonNull(subject));
            helper.setText(Objects.requireNonNull(body), isHtml);

            if (includeBrandLogo) {
                addInlineBrandLogo(helper);
            }

            if (!isBlank(config.getFromName())) {
                helper.setFrom(Objects.requireNonNull(config.getFromEmail()), Objects.requireNonNull(config.getFromName()).trim());
            } else {
                helper.setFrom(Objects.requireNonNull(config.getFromEmail()));
            }

            if (!isBlank(config.getReplyTo())) {
                helper.setReplyTo(Objects.requireNonNull(config.getReplyTo()).trim());
            }

            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.error("Failed to send email to {}", recipients, ex);
            return false;
        }
    }

    private void addInlineBrandLogo(MimeMessageHelper helper) {
        try {
            Resource logo = new ClassPathResource(BRAND_LOGO_CLASSPATH);
            if (!logo.exists() || !logo.isReadable()) {
                log.warn("Brand logo not found on classpath at {}", BRAND_LOGO_CLASSPATH);
                return;
            }
            helper.addInline("brandLogo", logo, "image/png");
        } catch (Exception ex) {
            log.warn("Unable to attach inline brand logo from {}", BRAND_LOGO_CLASSPATH, ex);
        }
    }

    private ValidationResult validatePayload(EmailConfigPayload payload, boolean includeSenderFields) {
        if (payload == null) {
            return ValidationResult.invalid("Configuration payload is required");
        }
        if (isBlank(payload.getHost())) {
            return ValidationResult.invalid("SMTP Host is required");
        }
        if (payload.getPort() == null) {
            return ValidationResult.invalid("SMTP Port is required");
        }
        if (payload.getPort() < 1 || payload.getPort() > 65535) {
            return ValidationResult.invalid("SMTP Port must be between 1 and 65535");
        }
        if (isBlank(payload.getUsername())) {
            return ValidationResult.invalid("Username / Email is required");
        }
        if (!isValidEmail(payload.getUsername())) {
            return ValidationResult.invalid("Username / Email must be a valid email address");
        }

        String normalizedEncryption = normalizeEncryption(payload.getEncryption());
        if (normalizedEncryption == null) {
            return ValidationResult.invalid("Encryption must be None, SSL, or TLS");
        }
        if (!isEncryptionCompatible(payload.getPort(), normalizedEncryption)) {
            return ValidationResult.invalid("Encryption must match the selected SMTP port");
        }

        if (includeSenderFields) {
            if (isBlank(payload.getFromEmail())) {
                return ValidationResult.invalid("From Email Address is required");
            }
            if (!isValidEmail(payload.getFromEmail())) {
                return ValidationResult.invalid("From Email Address must be a valid email address");
            }
            if (isBlank(payload.getFromName())) {
                return ValidationResult.invalid("From Name is required");
            }
            if (!isBlank(payload.getReplyTo()) && !isValidEmail(payload.getReplyTo())) {
                return ValidationResult.invalid("Reply-To Email must be a valid email address");
            }
        }

        return ValidationResult.valid();
    }

    private boolean isEncryptionCompatible(Integer port, String encryption) {
        if (port == null || encryption == null) {
            return false;
        }
        if (port == 465) {
            return "SSL".equals(encryption);
        }
        if (port == 587) {
            return "TLS".equals(encryption);
        }
        return true;
    }

    private String resolvePassword(String rawPassword, EmailConfig existing) {
        if (!isBlank(rawPassword) && !PASSWORD_MASK.equals(rawPassword.trim())) {
            return rawPassword.trim();
        }
        if (existing != null && existing.getEncryptedPassword() != null && !existing.getEncryptedPassword().isBlank()) {
            return cryptoService.decrypt(existing.getEncryptedPassword());
        }
        return null;
    }

    private JavaMailSenderImpl buildMailSender(EmailConfigPayload payload, String password) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(trim(payload.getHost()));
        mailSender.setPort(payload.getPort());
        mailSender.setUsername(trim(payload.getUsername()));
        mailSender.setPassword(password);

        String encryption = normalizeEncryption(payload.getEncryption());
        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");
        properties.put("mail.debug", "false");

        if ("SSL".equals(encryption)) {
            properties.put("mail.smtp.ssl.enable", "true");
            properties.put("mail.smtp.starttls.enable", "false");
            properties.put("mail.smtp.ssl.trust", trim(payload.getHost()));
        } else if ("TLS".equals(encryption)) {
            properties.put("mail.smtp.ssl.enable", "false");
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
            properties.put("mail.smtp.ssl.trust", trim(payload.getHost()));
        } else {
            properties.put("mail.smtp.ssl.enable", "false");
            properties.put("mail.smtp.starttls.enable", "false");
        }

        return mailSender;
    }

    private JavaMailSenderImpl buildMailSender(EmailConfig config, String password) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(trim(config.getHost()));
        mailSender.setPort(config.getPort());
        mailSender.setUsername(trim(config.getUsername()));
        mailSender.setPassword(password);

        String encryption = normalizeEncryption(config.getEncryption());
        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");
        properties.put("mail.debug", "false");

        if ("SSL".equals(encryption)) {
            properties.put("mail.smtp.ssl.enable", "true");
            properties.put("mail.smtp.starttls.enable", "false");
            properties.put("mail.smtp.ssl.trust", trim(config.getHost()));
        } else if ("TLS".equals(encryption)) {
            properties.put("mail.smtp.ssl.enable", "false");
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
            properties.put("mail.smtp.ssl.trust", trim(config.getHost()));
        } else {
            properties.put("mail.smtp.ssl.enable", "false");
            properties.put("mail.smtp.starttls.enable", "false");
        }

        return mailSender;
    }

    private boolean isRateLimited(String actorKey) {
        String key = isBlank(actorKey) ? "anonymous" : actorKey.trim().toLowerCase();
        long now = System.currentTimeMillis();
        Long previous = testRequestTimes.put(key, now);
        return previous != null && now - previous < testRateLimitMs;
    }

    private String resolveConnectionMessage(Exception ex) {
        return resolveConnectionMessage(ex, null);
    }

    private String resolveConnectionMessage(Exception ex, String host) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof AuthenticationFailedException) {
                return buildAuthFailedMessage(host);
            }
            if (current instanceof ConnectException || current instanceof SocketTimeoutException) {
                return "Unable to connect — check the SMTP host and port";
            }
            current = current.getCause();
        }
        String message = ex.getMessage();
        if (message != null) {
            String normalized = message.toLowerCase();
            if (normalized.contains("auth") || normalized.contains("username and password") || normalized.contains("535")) {
                return buildAuthFailedMessage(host);
            }
            if (normalized.contains("connect") || normalized.contains("timeout") || normalized.contains("host")) {
                return "Unable to connect — check the SMTP host and port";
            }
        }
        return "Unable to connect to the SMTP server";
    }

    private String buildAuthFailedMessage(String host) {
        if (!isBlank(host) && host.trim().toLowerCase().contains("gmail")) {
            return "Authentication failed. Gmail requires an App Password — go to myaccount.google.com/apppasswords, " +
                   "generate a 16-character App Password, and use that instead of your regular Google password.";
        }
        if (!isBlank(host) && host.trim().toLowerCase().contains("outlook")) {
            return "Authentication failed. Outlook/Office 365 may require an App Password or Modern Auth. " +
                   "Check that SMTP AUTH is enabled for your account in the admin portal.";
        }
        return "Authentication failed — check the username and password.";
    }

    private String normalizeEncryption(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "NONE" -> "NONE";
            case "SSL" -> "SSL";
            case "TLS" -> "TLS";
            default -> null;
        };
    }

    private boolean isValidEmail(String value) {
        return !isBlank(value) && EMAIL_PATTERN.matcher(value.trim()).matches();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        private boolean isValid() {
            return valid;
        }

        private String message() {
            return message;
        }

        private static ValidationResult valid() {
            return new ValidationResult(true, "");
        }

        private static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }
}
