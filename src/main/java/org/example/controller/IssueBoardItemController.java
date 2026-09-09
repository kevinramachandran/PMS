package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.entity.IssueBoardItem;
import org.example.entity.IssueBoardItemHistory;
import org.example.model.IssueBoardProgressUpdate;
import org.example.service.AuthService;
import org.example.service.IssueBoardItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/issue-board")
@CrossOrigin
public class IssueBoardItemController {

    private final IssueBoardItemService service;
    private final AuthService authService;

    public IssueBoardItemController(IssueBoardItemService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping("/date/{date}")
    public List<IssueBoardItem> getByDate(@PathVariable String date) {
        return service.getByBoardDate(LocalDate.parse(date));
    }

    @GetMapping("/latest")
    public List<IssueBoardItem> getLatest() {
        return service.getLatestBoard();
    }

    @GetMapping("/search")
    public List<IssueBoardItem> searchIssues(@RequestParam(defaultValue = "") String q) {
        return service.searchIssues(q);
    }

    @GetMapping("/{id}/history")
    public List<IssueBoardItemHistory> getHistory(@PathVariable Long id) {
        return service.getHistory(id);
    }

    @GetMapping("/assignable-users")
    public Map<String, Object> getAssignableUsers() {
        List<Map<String, String>> users = authService.getManageableUsers().stream()
                .map(user -> Map.of(
                        "username", defaultText(user.getUsername()),
                        "name", defaultText(user.getName()),
                        "employeeId", defaultText(user.getEmployeeId()),
                        "email", defaultText(user.getEmail()),
                        "label", buildUserLabel(user)
                ))
                .toList();

        return Map.of("status", "success", "users", users);
    }

    @PostMapping("/replace/date/{date}")
    public ResponseEntity<?> replaceByDate(
            @PathVariable String date,
            @RequestBody List<IssueBoardItem> items,
            HttpSession session) {
        String username = session == null ? null : (String) session.getAttribute("username");
        try {
            return ResponseEntity.ok(service.replaceByBoardDate(LocalDate.parse(date), items, username));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PatchMapping("/{id}/progress")
    public IssueBoardItem updateProgress(@PathVariable Long id,
                                         @RequestBody IssueBoardProgressUpdate update,
                                         HttpSession session) {
        String username = session == null ? null : (String) session.getAttribute("username");
        try {
            return service.updateProgress(id, update, username);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    private String buildUserLabel(org.example.entity.AppUser user) {
        String name = defaultText(user.getName());
        String username = defaultText(user.getUsername());
        String email = defaultText(user.getEmail());
        String display = !name.isBlank() ? name : username;
        return email.isBlank() ? display : display + " (" + email + ")";
    }

    private String defaultText(String value) {
        return value == null ? "" : value.trim();
    }
}
