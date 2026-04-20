package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.entity.AppLicense;
import org.example.model.LicenseGeneratePayload;
import org.example.service.AuthService;
import org.example.service.LicenseService;
import org.example.util.RoleAccess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/license")
public class LicenseController {

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private AuthService authService;

    /**
     * GET /api/license/current
     * Returns the active license from DB. Admin or internal user only.
     */
    @GetMapping("/current")
    public Map<String, Object> getCurrentLicense(HttpSession session) {
        if (!canViewLicense(session)) {
            return error("Forbidden");
        }

        Optional<AppLicense> current = licenseService.getCurrentLicense();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("license", current.map(licenseService::toResponse).orElse(null));
        return response;
    }

    /**
     * POST /api/license/generate
     * Open API — no session required. Generates an AES-encrypted license token
     * from the supplied payload and returns it WITHOUT saving to DB.
     *
     * Payload: { vendorName, dateFrom, dateTo, userCount, licenseText }
     */
    @PostMapping("/generate")
    public Map<String, Object> generateToken(@RequestBody LicenseGeneratePayload payload) {
        LicenseService.GenerateTokenResult result = licenseService.generateToken(payload);
        if (!result.success()) {
            return error(result.message());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "License token generated. Paste it in License Management UI to activate.");
        response.put("licenseToken", result.token());
        response.put("tokenLength", result.token().length());
        return response;
    }

    /**
     * POST /api/license/decode
     * Decodes an encrypted token and returns the embedded fields (does NOT save).
     * Admin or internal user only.
     *
     * Body: { "licenseToken": "..." }
     */
    @PostMapping("/decode")
    public Map<String, Object> decodeToken(@RequestBody Map<String, String> body, HttpSession session) {
        if (!canViewLicense(session)) {
            return error("Forbidden");
        }

        String token = body == null ? null : body.get("licenseToken");
        if (token == null || token.isBlank()) {
            return error("licenseToken is required");
        }

        LicenseService.DecodeTokenResult result = licenseService.decodeToken(token);
        if (!result.success()) {
            return error(result.message());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("fields", result.fields());
        return response;
    }

    /**
     * POST /api/license/save
     * Decodes the supplied token and persists it as the active license.
     * Admin or internal user only.
     *
     * Body: { "licenseToken": "..." }
     */
    @PostMapping("/save")
    public Map<String, Object> saveLicense(@RequestBody Map<String, String> body, HttpSession session) {
        if (!canEditLicense(session)) {
            return error("Forbidden");
        }

        String token = body == null ? null : body.get("licenseToken");
        if (token == null || token.isBlank()) {
            return error("licenseToken is required");
        }

        String actor = (String) session.getAttribute("username");
        LicenseService.SaveLicenseResult result = licenseService.saveFromToken(token, actor);
        if (!result.success()) {
            return error(result.message());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "License saved successfully.");
        response.put("license", licenseService.toResponse(result.license()));
        return response;
    }

    private boolean canViewLicense(HttpSession session) {
        if (session == null) return false;
        String role = (String) session.getAttribute("role");
        String username = (String) session.getAttribute("username");
        if (RoleAccess.isAdmin(role) || authService.isInternalStaticUser(username)) {
            return true;
        }
        return RoleAccess.canViewPage(role, extractPermissions(session, "viewPermissions"), RoleAccess.PAGE_LICENSE_MANAGEMENT);
    }

    private boolean canEditLicense(HttpSession session) {
        if (session == null) return false;
        String role = (String) session.getAttribute("role");
        String username = (String) session.getAttribute("username");
        if (RoleAccess.isAdmin(role) || authService.isInternalStaticUser(username)) {
            return true;
        }
        return RoleAccess.canEditPage(role, extractPermissions(session, "editPermissions"), RoleAccess.PAGE_LICENSE_MANAGEMENT);
    }

    private java.util.Set<String> extractPermissions(HttpSession session, String attributeName) {
        Object raw = session.getAttribute(attributeName);
        if (raw instanceof java.util.Set<?> setValue) {
            java.util.Set<String> values = setValue.stream()
                    .filter(value -> value != null)
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.toSet());
            return RoleAccess.sanitizePages(values);
        }
        return java.util.Set.of();
    }

    private Map<String, Object> error(String message) {
        return Map.of("status", "error", "message", message);
    }
}
