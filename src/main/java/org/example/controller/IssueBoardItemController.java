package org.example.controller;

import org.example.entity.IssueBoardItem;
import org.example.service.AuthService;
import org.example.service.IssueBoardItemService;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/assignable-users")
    public Map<String, Object> getAssignableUsers() {
        List<Map<String, String>> users = authService.getManageableUsers().stream()
                .map(user -> Map.of(
                        "username", user.getUsername(),
                        "email", user.getEmail(),
                        "label", user.getUsername() + " (" + user.getEmail() + ")"
                ))
                .toList();

        return Map.of("status", "success", "users", users);
    }

    @PostMapping("/replace/date/{date}")
    public List<IssueBoardItem> replaceByDate(
            @PathVariable String date,
            @RequestBody List<IssueBoardItem> items) {
        return service.replaceByBoardDate(LocalDate.parse(date), items);
    }
}
