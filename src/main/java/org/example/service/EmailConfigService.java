package org.example.service;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import org.example.entity.EmailConfig;
import org.example.model.EmailConfigPayload;
import org.example.model.EmailConfigResponse;
import org.example.model.EmailTestResponse;
import org.example.repository.EmailConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
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
        Optional<EmailConfig> existing = repository.findById(SINGLETON_ID);
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

        EmailConfig target = repository.findById(SINGLETON_ID).orElseGet(() -> {
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

        String password = resolvePassword(payload.getPassword(), repository.findById(SINGLETON_ID).orElse(null));
        if (password == null || password.isBlank()) {
            return new EmailTestResponse("ERROR", "Password is required");
        }

        JavaMailSenderImpl mailSender = buildMailSender(payload, password);
        try {
            mailSender.testConnection();
            return new EmailTestResponse("SUCCESS", "Connection successful");
        } catch (MailAuthenticationException ex) {
            return new EmailTestResponse("ERROR", "Authentication failed");
        } catch (MailSendException ex) {
            return new EmailTestResponse("ERROR", resolveConnectionMessage(ex));
        } catch (MessagingException ex) {
            return new EmailTestResponse("ERROR", resolveConnectionMessage(ex));
        }
    }

    public boolean hasStoredPassword() {
        return repository.findById(SINGLETON_ID)
                .map(config -> config.getEncryptedPassword() != null && !config.getEncryptedPassword().isBlank())
                .orElse(false);
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
        } else if ("TLS".equals(encryption)) {
            properties.put("mail.smtp.ssl.enable", "false");
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
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
        Throwable current = ex;
        while (current != null) {
            if (current instanceof AuthenticationFailedException) {
                return "Authentication failed";
            }
            if (current instanceof ConnectException || current instanceof SocketTimeoutException) {
                return "Invalid host/port";
            }
            current = current.getCause();
        }
        String message = ex.getMessage();
        if (message != null) {
            String normalized = message.toLowerCase();
            if (normalized.contains("auth")) {
                return "Authentication failed";
            }
            if (normalized.contains("connect") || normalized.contains("timeout") || normalized.contains("host")) {
                return "Invalid host/port";
            }
        }
        return "Unable to connect to the SMTP server";
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
