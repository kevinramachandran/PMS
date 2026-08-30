package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.entity.AbnormalityReportingRecord;
import org.example.service.AbnormalityReportingConfigService;
import org.example.util.RoleAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/abnormality-reporting-config")
public class AbnormalityReportingConfigController {

    private final AbnormalityReportingConfigService service;

    public AbnormalityReportingConfigController(AbnormalityReportingConfigService service) {
        this.service = service;
    }

    @GetMapping("/records")
    public ResponseEntity<?> records(HttpSession session) {
        if (!canView(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        return ResponseEntity.ok(Map.of("status", "success", "records", service.list()));
    }

    @GetMapping("/records/{id}")
    public ResponseEntity<?> record(@PathVariable Long id, HttpSession session) {
        if (!canView(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        return service.find(id)
                .<ResponseEntity<?>>map(item -> ResponseEntity.ok(Map.of("status", "success", "record", item)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Record not found")));
    }

    @PostMapping("/records")
    public ResponseEntity<?> create(@RequestBody AbnormalityReportingRecord request, HttpSession session) {
        if (!canView(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        try {
            return ResponseEntity.ok(Map.of("status", "success", "record", service.create(request, username(session))));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", ex.getMessage()));
        }
    }

    @PutMapping("/records/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody AbnormalityReportingRecord request,
                                    HttpSession session) {
        if (!canView(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        try {
            return service.update(id, request, username(session))
                    .<ResponseEntity<?>>map(record -> ResponseEntity.ok(Map.of("status", "success", "record", record)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Record not found")));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", ex.getMessage()));
        }
    }

    @GetMapping("/options")
    public ResponseEntity<?> options(HttpSession session) {
        if (!canView(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        return ResponseEntity.ok(Map.of("status", "success", "options", service.options()));
    }

    @GetMapping("/department-options")
    public ResponseEntity<?> departmentOptions(@RequestParam(value = "department", required = false) String department,
                                               HttpSession session) {
        if (!canView(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        return ResponseEntity.ok(Map.of("status", "success", "options", service.departmentOptions(department)));
    }

    private boolean canView(HttpSession session) {
        String role = session == null ? null : (String) session.getAttribute("role");
        return RoleAccess.canViewPage(role, permissions(session), RoleAccess.PAGE_ABNORMALITY_TRACKER_CONFIGURATION);
    }

    @SuppressWarnings("unchecked")
    private Set<String> permissions(HttpSession session) {
        Object raw = session == null ? null : session.getAttribute("viewPermissions");
        return raw instanceof Set<?> ? (Set<String>) raw : Set.of();
    }

    private String username(HttpSession session) {
        return session == null ? "" : String.valueOf(session.getAttribute("username"));
    }
}
