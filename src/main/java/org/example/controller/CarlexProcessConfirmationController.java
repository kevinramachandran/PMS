package org.example.controller;

import org.example.entity.CarlexProcessConfirmation;
import org.example.entity.AppUser;
import org.example.repository.AppUserRepository;
import org.example.service.CarlexProcessConfirmationService;
import org.example.entity.AssignmentHistory;
import org.example.service.AssignmentHistoryService;
import org.example.util.RoleAccess;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/carlex-process-confirmation")
@CrossOrigin
public class CarlexProcessConfirmationController {
    private final CarlexProcessConfirmationService service;
    private final AssignmentHistoryService assignmentHistoryService;
    private final AppUserRepository appUserRepository;

    public CarlexProcessConfirmationController(CarlexProcessConfirmationService service,
                                               AssignmentHistoryService assignmentHistoryService,
                                               AppUserRepository appUserRepository) {
        this.service = service;
        this.assignmentHistoryService = assignmentHistoryService;
        this.appUserRepository = appUserRepository;
    }

    @GetMapping("/records")
    public List<CarlexProcessConfirmation> list() { return service.list(); }

    @GetMapping("/options")
    public java.util.Map<String, Object> options(HttpSession session) {
        return Map.of(
                "assignmentUsers", hodUserOptions(),
                "currentUser", currentUserOption(username(session))
        );
    }

    @GetMapping("/records/{id}/history")
    public List<AssignmentHistory> history(@PathVariable Long id) { return assignmentHistoryService.history("carlex-process-confirmation", id); }

    @GetMapping("/records/{id}")
    public ResponseEntity<CarlexProcessConfirmation> get(@PathVariable Long id) {
        return service.get(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/records")
    public CarlexProcessConfirmation create(@RequestBody CarlexProcessConfirmation record, HttpSession session) { return service.create(record, username(session)); }

    @PutMapping("/records/{id}")
    public ResponseEntity<CarlexProcessConfirmation> update(@PathVariable Long id, @RequestBody CarlexProcessConfirmation record, HttpSession session) {
        return service.update(id, record, username(session)).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/records/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private String username(HttpSession session) {
        Object value = session == null ? null : session.getAttribute("username");
        return value == null ? "system" : String.valueOf(value);
    }

    private List<Map<String, String>> hodUserOptions() {
        return appUserRepository.findAll().stream()
                .filter(this::isActive)
                .filter(this::isHod)
                .map(this::userOption)
                .toList();
    }

    private java.util.Map<String, String> currentUserOption(String username) {
        return appUserRepository.findByUsernameIgnoreCase(username)
                .map(this::userOption)
                .orElse(Map.of("username", username, "label", username, "email", ""));
    }

    private java.util.Map<String, String> userOption(AppUser user) {
        String username = firstNonBlank(user.getUsername(), user.getEmail());
        String label = firstNonBlank(user.getName(), user.getUsername());
        return java.util.Map.of(
                "username", username,
                "label", label,
                "email", trim(user.getEmail())
        );
    }

    private boolean isActive(AppUser user) {
        return user != null && !"INACTIVE".equalsIgnoreCase(trim(user.getStatus()));
    }

    private boolean isHod(AppUser user) {
        String designation = trim(user.getDesignation()).toUpperCase(Locale.ENGLISH);
        return RoleAccess.isHod(user.getRole())
                || designation.contains("HOD")
                || designation.contains("HEAD_OF_DEPARTMENT");
    }

    private String firstNonBlank(String first, String second) {
        String trimmedFirst = trim(first);
        return trimmedFirst.isBlank() ? trim(second) : trimmedFirst;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
