package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.entity.AssignmentHistory;
import org.example.entity.CarlexProcessConfirmation;
import org.example.service.AssignmentHistoryService;
import org.example.service.CarlexProcessConfirmationService;
import org.example.util.RoleAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/carlex-process-confirmation")
@CrossOrigin
public class CarlexProcessConfirmationController {
    private final CarlexProcessConfirmationService service;
    private final AssignmentHistoryService assignmentHistoryService;

    public CarlexProcessConfirmationController(CarlexProcessConfirmationService service,
                                               AssignmentHistoryService assignmentHistoryService) {
        this.service = service;
        this.assignmentHistoryService = assignmentHistoryService;
    }

    @GetMapping("/records")
    public ResponseEntity<Map<String, Object>> list(HttpSession session) {
        if (!canView(session)) {
            return forbidden();
        }
        return ResponseEntity.ok(Map.of("records", service.listForUser(username(session), role(session))));
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> options(@RequestParam(value = "department", required = false) String department,
                                                       @RequestParam(value = "area", required = false) String area,
                                                       @RequestParam(value = "recordId", required = false) Long recordId,
                                                       HttpSession session) {
        if (!canView(session)) {
            return forbidden();
        }
        return ResponseEntity.ok(Map.of("options", service.options(username(session), role(session), department, area, recordId)));
    }

    @GetMapping("/records/{id}/history")
    public ResponseEntity<?> history(@PathVariable Long id, HttpSession session) {
        if (!canView(session)) {
            return forbidden();
        }
        if (service.get(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Not found"));
        }
        List<AssignmentHistory> rows = assignmentHistoryService.history("carlex-process-confirmation", id);
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/records/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id, HttpSession session) {
        if (!canView(session)) {
            return forbidden();
        }
        return service.get(id)
                .<ResponseEntity<Map<String, Object>>>map(record -> ResponseEntity.ok(Map.of("record", record)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Not found")));
    }

    @PostMapping("/records")
    public ResponseEntity<Map<String, Object>> create(@RequestBody CarlexProcessConfirmation record, HttpSession session) {
        if (!canView(session)) {
            return forbidden();
        }
        try {
            return ResponseEntity.ok(Map.of("status", "success", "record", service.create(record, username(session), role(session))));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", ex.getMessage()));
        }
    }

    @PutMapping("/records/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @RequestBody CarlexProcessConfirmation record,
                                                      HttpSession session) {
        if (!canView(session)) {
            return forbidden();
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
        if (!canView(session)) {
            return forbidden();
        }
        try {
            return service.delete(id, username(session), role(session))
                    ? ResponseEntity.ok(Map.of("status", "success"))
                    : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Not found"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", ex.getMessage()));
        }
    }

    private ResponseEntity<Map<String, Object>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
    }

    private boolean canView(HttpSession session) {
        return RoleAccess.canViewPage(role(session), permissions(session), RoleAccess.PAGE_PROCESS_CONFIRMATION_CONFIGURATION);
    }

    private String username(HttpSession session) {
        Object value = session == null ? null : session.getAttribute("username");
        return value == null ? "" : String.valueOf(value);
    }

    private String role(HttpSession session) {
        Object value = session == null ? null : session.getAttribute("role");
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Set<String> permissions(HttpSession session) {
        Object value = session == null ? null : session.getAttribute("viewPermissions");
        return value instanceof Set<?> ? (Set<String>) value : Set.of();
    }
}
