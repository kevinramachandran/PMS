package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.entity.AppUser;
import org.example.model.UserInfo;
import org.example.service.AuthService;
import org.example.util.RoleAccess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class AuthController {

    @Autowired
    private AuthService authService;

    /** Show the login page, or redirect if already logged in. */
    @GetMapping("/pms-login")
    public String loginPage(HttpSession session) {
        if (session.getAttribute("username") != null) {
            return "redirect:/kpi-dashboard";
        }
        return "login";
    }

    /**
     * Validate credentials and store session.
     * POST /api/auth/login  { "username": "...", "password": "..." }
     */
    @PostMapping("/api/auth/login")
    @ResponseBody
    public Map<String, String> login(@RequestBody Map<String, String> credentials,
                                     HttpSession session) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        Optional<UserInfo> user = authService.authenticate(username, password);
        if (user.isPresent()) {
            String normalizedRole = RoleAccess.normalize(user.get().getRole());
            session.setAttribute("username", user.get().getUsername());
            session.setAttribute("role", normalizedRole);
            session.setAttribute("roleLabel", RoleAccess.displayName(normalizedRole));
            session.setAttribute("email",    user.get().getEmail());
            return Map.of(
                    "status",   "success",
                    "role", normalizedRole,
                    "roleLabel", RoleAccess.displayName(normalizedRole),
                "username", user.get().getUsername(),
                "email",    user.get().getEmail()
            );
        }
        return Map.of("status", "error", "message", "Invalid credentials");
    }

    /** Invalidate session and redirect to login page. */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/pms-login";
    }

    @GetMapping("/api/users")
    @ResponseBody
    public Map<String, Object> listUsers(HttpSession session) {
        if (!isAdmin(session)) {
            return Map.of("status", "error", "message", "Forbidden");
        }

        List<AppUser> users = authService.getManageableUsers();
        List<Map<String, Object>> result = users.stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());

        return Map.of("status", "success", "users", result);
    }

    @PostMapping("/api/users")
    @ResponseBody
    public Map<String, String> addUser(@RequestBody Map<String, String> payload,
                                       HttpSession session) {
        if (!isAdmin(session)) {
            return Map.of("status", "error", "message", "Forbidden");
        }

        String username = payload.get("username");
        String email = payload.get("email");
        String password = payload.get("password");
        String newRole = payload.get("role");

        Optional<String> validation = authService.addUser(username, email, password, newRole);
        if (validation.isPresent()) {
            return Map.of("status", "error", "message", validation.get());
        }

        return Map.of("status", "success", "message", "User added successfully");
    }

    @PutMapping("/api/users/{id}")
    @ResponseBody
    public Map<String, String> updateUser(@PathVariable Long id,
                                          @RequestBody Map<String, String> payload,
                                          HttpSession session) {
        if (!isAdmin(session)) {
            return Map.of("status", "error", "message", "Forbidden");
        }

        String email = payload.get("email");
        String password = payload.get("password");
        String role = payload.get("role");

        Optional<String> updateError = authService.updateUser(id, email, password, role);
        if (updateError.isPresent()) {
            return Map.of("status", "error", "message", updateError.get());
        }

        return Map.of("status", "success", "message", "User updated successfully");
    }

    @DeleteMapping("/api/users/{id}")
    @ResponseBody
    public Map<String, String> deleteUser(@PathVariable Long id,
                                          HttpSession session) {
        if (!isAdmin(session)) {
            return Map.of("status", "error", "message", "Forbidden");
        }

        Optional<String> deleteError = authService.deleteUser(id);
        if (deleteError.isPresent()) {
            return Map.of("status", "error", "message", deleteError.get());
        }

        return Map.of("status", "success", "message", "User deleted successfully");
    }

    private boolean isAdmin(HttpSession session) {
        String role = (String) session.getAttribute("role");
        return RoleAccess.isAdmin(role);
    }

    private Map<String, Object> toUserResponse(AppUser user) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", user.getId());
        row.put("username", user.getUsername());
        row.put("email", user.getEmail());
        row.put("role", RoleAccess.normalize(user.getRole()));
        row.put("roleLabel", RoleAccess.displayName(user.getRole()));
        row.put("status", "Active");
        return row;
    }
}
