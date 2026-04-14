package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.model.EmailConfigPayload;
import org.example.model.EmailConfigResponse;
import org.example.model.EmailTestResponse;
import org.example.service.EmailConfigService;
import org.example.util.RoleAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/email-config")
public class EmailConfigController {

    private final EmailConfigService emailConfigService;

    public EmailConfigController(EmailConfigService emailConfigService) {
        this.emailConfigService = emailConfigService;
    }

    @GetMapping
    public ResponseEntity<?> getConfiguration(HttpSession session) {
        if (!hasAccess(session)) {
            return forbidden();
        }

        EmailConfigResponse response = emailConfigService.getConfiguration();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> saveConfiguration(@RequestBody EmailConfigPayload payload, HttpSession session) {
        if (!hasAccess(session)) {
            return forbidden();
        }

        try {
            emailConfigService.saveConfiguration(payload);
            return ResponseEntity.ok(Map.of("message", "Configuration saved successfully"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/test")
    public ResponseEntity<EmailTestResponse> testConfiguration(@RequestBody EmailConfigPayload payload, HttpSession session) {
        if (!hasAccess(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new EmailTestResponse("ERROR", "Forbidden"));
        }

        String actorKey = (String) session.getAttribute("username");
        EmailTestResponse response = emailConfigService.testConfiguration(payload, actorKey);
        HttpStatus status = "SUCCESS".equals(response.getStatus()) ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        if ("Forbidden".equals(response.getMessage())) {
            status = HttpStatus.FORBIDDEN;
        }
        return ResponseEntity.status(status).body(response);
    }

    private boolean hasAccess(HttpSession session) {
        String role = session == null ? null : (String) session.getAttribute("role");
        return RoleAccess.canAccessEmailConfiguration(role);
    }

    private ResponseEntity<Map<String, String>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Forbidden"));
    }
}
