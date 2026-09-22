package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.entity.GembaKaizenRecord;
import org.example.entity.AssignmentHistory;
import org.example.service.GembaKaizenConfigService;
import org.example.service.AssignmentHistoryService;
import org.example.util.RoleAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/gemba-kaizen-config")
public class GembaKaizenConfigController {

    private final GembaKaizenConfigService service;
    private final AssignmentHistoryService assignmentHistoryService;

    public GembaKaizenConfigController(GembaKaizenConfigService service, AssignmentHistoryService assignmentHistoryService) {
        this.service = service;
        this.assignmentHistoryService = assignmentHistoryService;
    }

    @GetMapping("/records")
    public ResponseEntity<Map<String, Object>> records(HttpSession session) {
        if (!canView(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        return ResponseEntity.ok(Map.of("records", service.listForUser(username(session), role(session))));
    }

    @GetMapping("/records/{id}")
    public ResponseEntity<Map<String, Object>> record(@PathVariable Long id, HttpSession session) {
        if (!canView(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        return service.find(id)
                .<ResponseEntity<Map<String, Object>>>map(record -> ResponseEntity.ok(Map.of("record", record)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Not found")));
    }

    @GetMapping("/records/{id}/history")
    public ResponseEntity<?> history(@PathVariable Long id, HttpSession session) {
        if (!canView(session)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        if (service.find(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Not found"));
        }
        return ResponseEntity.ok(assignmentHistoryService.history("gemba-kaizen", id));
    }

    @PostMapping("/records")
    public ResponseEntity<Map<String, Object>> create(@RequestBody GembaKaizenRecord record, HttpSession session) {
        if (!canEdit(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        try {
            return ResponseEntity.ok(Map.of("status", "success", "record", service.create(record, username(session), role(session))));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", ex.getMessage()));
        }
    }

    @PutMapping("/records/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @RequestBody GembaKaizenRecord record,
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

    @DeleteMapping("/records/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, HttpSession session) {
        if (!canEdit(session)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        return service.delete(id, username(session), role(session))
                ? ResponseEntity.ok(Map.of("status", "success"))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Not found"));
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> options(@RequestParam(value = "department", required = false) String department,
                                                       @RequestParam(value = "location", required = false) String location,
                                                       @RequestParam(value = "recordId", required = false) Long recordId,
                                                       HttpSession session) {
        if (!canView(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        return ResponseEntity.ok(Map.of("options", service.options(username(session), role(session), department, location, recordId)));
    }

    private boolean canEdit(HttpSession session) {
        if (session == null) return false;
        Object raw = session.getAttribute("editPermissions");
        Set<String> edits = raw instanceof Set<?> values ? values.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet()) : Set.of();
        return RoleAccess.canEditPage(role(session), edits, RoleAccess.PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION);
    }

    private boolean canView(HttpSession session) {
        String role = session == null ? null : (String) session.getAttribute("role");
        return RoleAccess.canViewPage(role, permissions(session), RoleAccess.PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION);
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
