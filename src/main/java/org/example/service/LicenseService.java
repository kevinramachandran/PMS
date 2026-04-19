package org.example.service;

import org.example.entity.AppLicense;
import org.example.model.LicenseGeneratePayload;
import org.example.repository.AppLicenseRepository;
import org.example.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class LicenseService {

    // 16-byte AES-128 key (must remain constant across restarts)
    private static final byte[] AES_KEY = "PMS@L1c$K3y#2025".getBytes(StandardCharsets.UTF_8);
    private static final String CIPHER_ALGO = "AES/ECB/PKCS5Padding";
    private static final String FIELD_SEP = "\u001F"; // ASCII unit separator — safe delimiter
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Autowired
    private AppLicenseRepository appLicenseRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    public Optional<AppLicense> getCurrentLicense() {
        return appLicenseRepository.findTopByOrderByIdDesc();
    }

    public LicenseGateResult evaluateForLogin(boolean internalStaticUser) {
        if (internalStaticUser) {
            return LicenseGateResult.allow("Internal static user bypass");
        }

        Optional<AppLicense> current = getCurrentLicense();
        if (current.isEmpty()) {
            return LicenseGateResult.deny("License is not configured. Contact internal administrator.", "LICENSE_NOT_CONFIGURED");
        }

        AppLicense license = current.get();
        LocalDate today = LocalDate.now();
        if (license.getDateFrom() != null && today.isBefore(license.getDateFrom())) {
            return LicenseGateResult.deny("License is not active yet.", "LICENSE_NOT_STARTED");
        }

        if (license.getDateTo() != null && today.isAfter(license.getDateTo())) {
            return LicenseGateResult.deny("License has expired. Contact internal administrator.", "LICENSE_EXPIRED");
        }

        long licensedUsers = license.getUserCount() == null ? 0 : license.getUserCount();
        long managedUsers = appUserRepository.count();
        if (licensedUsers > 0 && managedUsers > licensedUsers) {
            return LicenseGateResult.deny("License user limit exceeded. Contact internal administrator.", "LICENSE_USER_LIMIT_EXCEEDED");
        }

        return LicenseGateResult.allow("License valid");
    }

    public Optional<String> validateManagedUserCreation() {
        Optional<AppLicense> current = getCurrentLicense();
        if (current.isEmpty()) {
            return Optional.of("License is not configured. Add license before creating users.");
        }

        AppLicense license = current.get();
        LocalDate today = LocalDate.now();
        if (license.getDateFrom() != null && today.isBefore(license.getDateFrom())) {
            return Optional.of("License is not active yet.");
        }
        if (license.getDateTo() != null && today.isAfter(license.getDateTo())) {
            return Optional.of("License has expired. Cannot add users.");
        }

        long licensedUsers = license.getUserCount() == null ? 0 : license.getUserCount();
        long managedUsers = appUserRepository.count();
        if (licensedUsers > 0 && managedUsers >= licensedUsers) {
            return Optional.of("Licensed user limit reached (" + licensedUsers + ").");
        }

        return Optional.empty();
    }

    // ==================== TOKEN GENERATION (API-only, no DB save) ====================

    /**
     * Generates an AES-128 encrypted license token (~100 chars) from the payload.
     * Does NOT persist anything — purely computational.
     */
    public GenerateTokenResult generateToken(LicenseGeneratePayload payload) {
        Optional<String> validation = validatePayload(payload);
        if (validation.isPresent()) {
            return GenerateTokenResult.error(validation.get());
        }

        try {
            String inner = buildInner(payload);
            String token = encrypt(inner);
            return GenerateTokenResult.success(token);
        } catch (Exception e) {
            return GenerateTokenResult.error("Token generation failed: " + e.getMessage());
        }
    }

    /**
     * Decodes an encrypted license token and returns the embedded fields.
     */
    public DecodeTokenResult decodeToken(String token) {
        if (token == null || token.isBlank()) {
            return DecodeTokenResult.error("Token is required");
        }
        try {
            String normalizedToken = normalizeToken(token);
            String inner = decrypt(normalizedToken);
            String[] parts = inner.split(FIELD_SEP, -1);
            if (parts.length != 5) {
                return DecodeTokenResult.error("Invalid token format");
            }

            String vendorName  = parts[0];
            LocalDate dateFrom = LocalDate.parse(parts[1], DATE_FMT);
            LocalDate dateTo   = LocalDate.parse(parts[2], DATE_FMT);
            int userCount      = Integer.parseInt(parts[3]);
            String licenseText = parts[4];

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("vendorName",  vendorName);
            fields.put("dateFrom",    dateFrom.toString());
            fields.put("dateTo",      dateTo.toString());
            fields.put("userCount",   userCount);
            fields.put("licenseText", licenseText);
            return DecodeTokenResult.success(fields);
        } catch (Exception e) {
            return DecodeTokenResult.error("Invalid or tampered token");
        }
    }

    // ==================== SAVE / PERSIST ====================

    /**
     * Decodes the encrypted token and persists it as the active license.
     */
    @Transactional
    public SaveLicenseResult saveFromToken(String token, String actor) {
        String normalizedToken = normalizeToken(token);
        DecodeTokenResult decoded = decodeToken(normalizedToken);
        if (!decoded.success()) {
            return SaveLicenseResult.error(decoded.message());
        }

        Map<String, Object> f = decoded.fields();
        AppLicense license = appLicenseRepository.findByLicenseToken(normalizedToken)
                .or(() -> appLicenseRepository.findTopByOrderByIdDesc())
                .orElse(new AppLicense());

        license.setVendorName((String) f.get("vendorName"));
        license.setDateFrom(LocalDate.parse((String) f.get("dateFrom"), DATE_FMT));
        license.setDateTo(LocalDate.parse((String) f.get("dateTo"), DATE_FMT));
        license.setUserCount((Integer) f.get("userCount"));
        license.setLicenseText((String) f.get("licenseText"));
        license.setLicenseToken(normalizedToken);
        license.setCreatedBy(actor == null ? "" : actor.trim());

        AppLicense saved = appLicenseRepository.save(license);
        return SaveLicenseResult.success(saved);
    }

    public Map<String, Object> toResponse(AppLicense license) {
        Map<String, Object> res = new LinkedHashMap<>();
        if (license == null) {
            return res;
        }

        LocalDate today = LocalDate.now();
        String status;
        if (license.getDateFrom() != null && today.isBefore(license.getDateFrom())) {
            status = "NOT_STARTED";
        } else if (license.getDateTo() != null && today.isAfter(license.getDateTo())) {
            status = "EXPIRED";
        } else {
            status = "ACTIVE";
        }

        res.put("id", license.getId());
        res.put("vendorName", license.getVendorName());
        res.put("dateFrom", license.getDateFrom());
        res.put("dateTo", license.getDateTo());
        res.put("userCount", license.getUserCount());
        res.put("licenseText", license.getLicenseText());
        res.put("licenseToken", license.getLicenseToken());
        res.put("status", status);
        res.put("createdBy", license.getCreatedBy());
        res.put("createdAt", license.getCreatedAt());
        res.put("updatedAt", license.getUpdatedAt());
        res.put("managedUsersCount", appUserRepository.count());
        return res;
    }

    // ==================== PRIVATE HELPERS ====================

    private String buildInner(LicenseGeneratePayload payload) {
        return payload.getVendorName().trim()
                + FIELD_SEP + payload.getDateFrom().format(DATE_FMT)
                + FIELD_SEP + payload.getDateTo().format(DATE_FMT)
                + FIELD_SEP + payload.getUserCount()
                + FIELD_SEP + payload.getLicenseText().trim();
    }

    private String encrypt(String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private String decrypt(String token) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"));
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(token));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private String normalizeToken(String token) {
        return token == null ? "" : token.replaceAll("\\s+", "").trim();
    }

    private Optional<String> validatePayload(LicenseGeneratePayload payload) {
        if (payload == null) {
            return Optional.of("License payload is required");
        }
        if (payload.getVendorName() == null || payload.getVendorName().trim().isEmpty()) {
            return Optional.of("Vendor name is required");
        }
        if (payload.getDateFrom() == null || payload.getDateTo() == null) {
            return Optional.of("Date from and date to are required");
        }
        if (payload.getDateFrom().isAfter(payload.getDateTo())) {
            return Optional.of("Date from cannot be after date to");
        }
        if (payload.getUserCount() == null || payload.getUserCount() <= 0) {
            return Optional.of("User count must be greater than 0");
        }
        if (payload.getLicenseText() == null || payload.getLicenseText().trim().isEmpty()) {
            return Optional.of("License text is required");
        }
        return Optional.empty();
    }

    // ==================== RESULT RECORDS ====================

    public record GenerateTokenResult(boolean success, String token, String message) {
        public static GenerateTokenResult success(String token) {
            return new GenerateTokenResult(true, token, "Token generated successfully");
        }
        public static GenerateTokenResult error(String message) {
            return new GenerateTokenResult(false, null, message);
        }
    }

    public record DecodeTokenResult(boolean success, Map<String, Object> fields, String message) {
        public static DecodeTokenResult success(Map<String, Object> fields) {
            return new DecodeTokenResult(true, fields, "Token decoded successfully");
        }
        public static DecodeTokenResult error(String message) {
            return new DecodeTokenResult(false, null, message);
        }
    }

    public record SaveLicenseResult(boolean success, AppLicense license, String message) {
        public static SaveLicenseResult success(AppLicense license) {
            return new SaveLicenseResult(true, license, "License saved successfully");
        }
        public static SaveLicenseResult error(String message) {
            return new SaveLicenseResult(false, null, message);
        }
    }

    public record LicenseGateResult(boolean allowed, String message, String code) {
        public static LicenseGateResult allow(String message) {
            return new LicenseGateResult(true, message, "OK");
        }

        public static LicenseGateResult deny(String message, String code) {
            return new LicenseGateResult(false, message, code);
        }
    }
}
