package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.entity.GembaWalkRecord;
import org.example.service.GembaWalkConfigService;
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

    public GembaWalkConfigController(GembaWalkConfigService service) {
        this.service = service;
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

    @PostMapping("/records")
    public ResponseEntity<Map<String, Object>> create(@RequestBody GembaWalkRecord record, HttpSession session) {
        if (!canView(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        GembaWalkRecord saved = service.create(record, username(session));
        return ResponseEntity.ok(Map.of("status", "success", "record", saved));
    }

    @PutMapping("/records/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @RequestBody GembaWalkRecord record,
                                                      HttpSession session) {
        if (!canView(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        return service.update(id, record, username(session))
                .<ResponseEntity<Map<String, Object>>>map(saved -> ResponseEntity.ok(Map.of("status", "success", "record", saved)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Not found")));
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> options(@RequestParam(value = "location", required = false) String location,
                                                       HttpSession session) {
        if (!canView(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "error", "message", "Forbidden"));
        }
        return ResponseEntity.ok(Map.of("options", service.options(username(session), location)));
    }

    private boolean canView(HttpSession session) {
        String role = session == null ? null : (String) session.getAttribute("role");
        return RoleAccess.canViewPage(role, permissions(session), RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION);
    }

    private String username(HttpSession session) {
        return session == null ? "" : String.valueOf(session.getAttribute("username"));
    }

    @SuppressWarnings("unchecked")
    private Set<String> permissions(HttpSession session) {
        Object raw = session == null ? null : session.getAttribute("viewPermissions");
        return raw instanceof Set<?> ? (Set<String>) raw : Set.of();
    }
}
