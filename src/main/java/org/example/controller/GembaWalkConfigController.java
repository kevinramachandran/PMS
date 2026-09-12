package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.entity.GembaWalkRecord;
import org.example.entity.AssignmentHistory;
import org.example.service.GembaWalkConfigService;
import org.example.service.AssignmentHistoryService;
import org.example.util.RoleAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/gemba-walk-config")
public class GembaWalkConfigController {

    private final GembaWalkConfigService service;
    private final AssignmentHistoryService assignmentHistoryService;

    public GembaWalkConfigController(GembaWalkConfigService service, AssignmentHistoryService assignmentHistoryService) {
        this.service = service;
        this.assignmentHistoryService = assignmentHistoryService;
    }

    @GetMapping("/records")
    public ResponseEntity<Map<String, Object>> records(HttpSession session) {
        if (!canView(session) && !canViewReporting(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        return ResponseEntity.ok(Map.of("records", service.listForUser(username(session), role(session))));
    }

    @GetMapping("/records/{id}")
    public ResponseEntity<Map<String, Object>> record(@PathVariable Long id, HttpSession session) {
        if (!canView(session) && !canViewReporting(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        return service.find(id)
                .<ResponseEntity<Map<String, Object>>>map(record -> ResponseEntity.ok(Map.of("record", record)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Not found")));
    }

    @GetMapping("/records/{id}/history")
    public ResponseEntity<?> history(@PathVariable Long id, HttpSession session) {
        if (!canView(session) && !canViewReporting(session)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        if (service.find(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Not found"));
        }
        return ResponseEntity.ok(assignmentHistoryService.history("gemba-walk", id));
    }

    @PostMapping("/records")
    public ResponseEntity<Map<String, Object>> create(@RequestBody GembaWalkRecord record, HttpSession session) {
        if (!canView(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        try {
            GembaWalkRecord saved = service.create(record, username(session), role(session));
            return ResponseEntity.ok(Map.of("status", "success", "record", saved));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", ex.getMessage()));
        }
    }

    @PutMapping("/records/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @RequestBody GembaWalkRecord record,
                                                      HttpSession session) {
        if (!canView(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        try {
            return service.update(id, record, username(session), role(session))
                    .<ResponseEntity<Map<String, Object>>>map(saved -> ResponseEntity.ok(Map.of("status", "success", "record", saved)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Not found")));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", ex.getMessage()));
        }
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> options(@RequestParam(value = "department", required = false) String department,
                                                       @RequestParam(value = "location", required = false) String location,
                                                       @RequestParam(value = "recordId", required = false) Long recordId,
                                                       HttpSession session) {
        if (!canView(session) && !canViewReporting(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        return ResponseEntity.ok(Map.of("options", service.options(username(session), role(session), department, location, recordId)));
    }

    private boolean canView(HttpSession session) {
        String role = session == null ? null : (String) session.getAttribute("role");
        return RoleAccess.canViewPage(role, permissions(session), RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION);
    }

    private boolean canViewReporting(HttpSession session) {
        String role = session == null ? null : (String) session.getAttribute("role");
        return RoleAccess.canViewPage(role, permissions(session), RoleAccess.PAGE_GEMBA_WALK_REPORTING);
    }

    private String username(HttpSession session) {
        Object raw = session == null ? null : session.getAttribute("username");
        return raw == null ? "" : String.valueOf(raw);
    }

    private String role(HttpSession session) {
        Object raw = session == null ? null : session.getAttribute("role");
        return raw == null ? "" : String.valueOf(raw);
    }

    @SuppressWarnings("unchecked")
    private Set<String> permissions(HttpSession session) {
        Object raw = session == null ? null : session.getAttribute("viewPermissions");
        return raw instanceof Set<?> ? (Set<String>) raw : Set.of();
    }
}
