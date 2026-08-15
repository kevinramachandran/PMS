package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.entity.AppUser;
import org.example.model.UserInfo;
import org.example.service.AuthService;
import org.example.service.LicenseService;
import org.example.util.RoleAccess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private LicenseService licenseService;

    /** Show the login page, or redirect if already logged in. */
    @GetMapping("/pms-login")
    public String loginPage(HttpSession session) {
        if (session.getAttribute("username") != null) {
            return "redirect:" + landingPageForSession(session);
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
            boolean licenseBypassUser = authService.isLicenseBypassUser(user.get().getUsername());
            LicenseService.LicenseGateResult gate = licenseService.evaluateForLogin(licenseBypassUser);
            if (!gate.allowed()) {
                return Map.of("status", "error", "message", gate.message(), "code", gate.code());
            }

            String normalizedRole = RoleAccess.normalize(user.get().getRole());
            Set<String> viewPermissions = user.get().getViewPermissions();
            Set<String> editPermissions = user.get().getEditPermissions();
            session.setAttribute("username", user.get().getUsername());
            session.setAttribute("role", normalizedRole);
            session.setAttribute("roleLabel", RoleAccess.displayName(normalizedRole));
            session.setAttribute("email",    user.get().getEmail());
            session.setAttribute("viewPermissions", viewPermissions);
            session.setAttribute("editPermissions", editPermissions);
            applyPermissionSessionAttributes(session, normalizedRole, viewPermissions, editPermissions);
            return Map.of(
                    "status",   "success",
                    "role", normalizedRole,
                    "roleLabel", RoleAccess.displayName(normalizedRole),
                "username", user.get().getUsername(),
                "email",    user.get().getEmail(),
                "redirectUrl", landingPageFor(normalizedRole, viewPermissions)
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
        if (!canViewPage(session, RoleAccess.PAGE_USER_MANAGEMENT)) {
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
    public Map<String, String> addUser(@RequestBody Map<String, Object> payload,
                                       HttpSession session) {
        if (!canEditPage(session, RoleAccess.PAGE_USER_MANAGEMENT)) {
            return Map.of("status", "error", "message", "Forbidden");
        }

        String username = asString(payload.get("username"));
        String email = asString(payload.get("email"));
        String password = asString(payload.get("password"));
        String newRole = asString(payload.get("role"));
        Set<String> viewPermissions = toPermissionSet(payload.get("viewPermissions"));
        Set<String> editPermissions = toPermissionSet(payload.get("editPermissions"));

        Optional<String> validation = authService.addUser(username, email, password, newRole, viewPermissions, editPermissions);
        if (validation.isPresent()) {
            return Map.of("status", "error", "message", validation.get());
        }

        return Map.of("status", "success", "message", "User added successfully");
    }

    @PutMapping("/api/users/{id}")
    @ResponseBody
    public Map<String, String> updateUser(@PathVariable Long id,
                                          @RequestBody Map<String, Object> payload,
                                          HttpSession session) {
        if (!canEditPage(session, RoleAccess.PAGE_USER_MANAGEMENT)) {
            return Map.of("status", "error", "message", "Forbidden");
        }

        String email = asString(payload.get("email"));
        String password = asString(payload.get("password"));
        String role = asString(payload.get("role"));
        Set<String> viewPermissions = toPermissionSet(payload.get("viewPermissions"));
        Set<String> editPermissions = toPermissionSet(payload.get("editPermissions"));

        Optional<String> updateError = authService.updateUser(id, email, password, role, viewPermissions, editPermissions);
        if (updateError.isPresent()) {
            return Map.of("status", "error", "message", updateError.get());
        }

        return Map.of("status", "success", "message", "User updated successfully");
    }

    @DeleteMapping("/api/users/{id}")
    @ResponseBody
    public Map<String, String> deleteUser(@PathVariable Long id,
                                          HttpSession session) {
        if (!canEditPage(session, RoleAccess.PAGE_USER_MANAGEMENT)) {
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

    private boolean canViewPage(HttpSession session, String pageKey) {
        if (session == null) {
            return false;
        }
        String role = (String) session.getAttribute("role");
        return RoleAccess.canViewPage(role, toPermissionSet(session.getAttribute("viewPermissions")), pageKey);
    }

    private boolean canEditPage(HttpSession session, String pageKey) {
        if (session == null) {
            return false;
        }
        String role = (String) session.getAttribute("role");
        return RoleAccess.canEditPage(role, toPermissionSet(session.getAttribute("editPermissions")), pageKey);
    }

    private void applyPermissionSessionAttributes(HttpSession session,
                                                  String role,
                                                  Set<String> viewPermissions,
                                                  Set<String> editPermissions) {
        boolean canViewPmsDataEntry = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_PMS_DATA_ENTRY);
        boolean canViewProductionMetricsData = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_PRODUCTION_METRICS_DATA);
        boolean canViewIssueBoardConfiguration = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_ISSUE_BOARD_CONFIGURATION);
        boolean canViewGembaWalkConfiguration = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION);
        boolean canViewLeadershipGembaTrackerConfiguration = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION);
        boolean canViewTrainingScheduleConfiguration = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_TRAINING_SCHEDULE_CONFIGURATION);
        boolean canViewMeetingAgendaConfiguration = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_MEETING_AGENDA_CONFIGURATION);
        boolean canViewProcessConfirmationConfiguration = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_PROCESS_CONFIRMATION_CONFIGURATION);
        boolean canViewAbnormalityTrackerConfiguration = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_ABNORMALITY_TRACKER_CONFIGURATION);
        boolean canViewHsCrossDailyConfiguration = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_HS_CROSS_DAILY_CONFIGURATION);
        boolean canViewLsrTrackingConfiguration = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_LSR_TRACKING_CONFIGURATION);
        boolean canViewInfoPortal = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_INFO_PORTAL);
        boolean canViewKpiTargetCrossColor = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_KPI_TARGET_CROSS_COLOR);
        boolean canViewKpiRenameDashboard = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_KPI_RENAME_DASHBOARD);
        boolean canViewKpiPlantName = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_KPI_PLANT_NAME);
        boolean canViewUserManagement = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_USER_MANAGEMENT);
        boolean canViewLicenseManagement = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_LICENSE_MANAGEMENT);
        boolean canViewEmailConfiguration = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_EMAIL_CONFIGURATION);
        boolean canEditUserManagement = RoleAccess.canEditPage(role, editPermissions, RoleAccess.PAGE_USER_MANAGEMENT);
        boolean canEditIssueBoardConfiguration = RoleAccess.canEditPage(role, editPermissions, RoleAccess.PAGE_ISSUE_BOARD_CONFIGURATION);

        session.setAttribute("canViewSettings", RoleAccess.canViewAnyConfigurationPage(role, viewPermissions));
        session.setAttribute("canEditSettings", RoleAccess.canEditAnyConfigurationPage(role, editPermissions));
        session.setAttribute("canViewPmsDataEntry", canViewPmsDataEntry);
        session.setAttribute("canViewProductionMetricsData", canViewProductionMetricsData);
        session.setAttribute("canViewIssueBoardConfiguration", canViewIssueBoardConfiguration);
        session.setAttribute("canEditIssueBoardConfiguration", canEditIssueBoardConfiguration);
        session.setAttribute("canViewGembaWalkConfiguration", canViewGembaWalkConfiguration);
        session.setAttribute("canViewLeadershipGembaTrackerConfiguration", canViewLeadershipGembaTrackerConfiguration);
        session.setAttribute("canViewTrainingScheduleConfiguration", canViewTrainingScheduleConfiguration);
        session.setAttribute("canViewMeetingAgendaConfiguration", canViewMeetingAgendaConfiguration);
        session.setAttribute("canViewProcessConfirmationConfiguration", canViewProcessConfirmationConfiguration);
        session.setAttribute("canViewAbnormalityTrackerConfiguration", canViewAbnormalityTrackerConfiguration);
        session.setAttribute("canViewHsCrossDailyConfiguration", canViewHsCrossDailyConfiguration);
        session.setAttribute("canViewLsrTrackingConfiguration", canViewLsrTrackingConfiguration);
        session.setAttribute("canViewInfoPortal", canViewInfoPortal);
        session.setAttribute("canViewKpiTargetCrossColor", canViewKpiTargetCrossColor);
        session.setAttribute("canViewKpiRenameDashboard", canViewKpiRenameDashboard);
        session.setAttribute("canViewKpiPlantName", canViewKpiPlantName);
        session.setAttribute("canViewUserManagement", canViewUserManagement);
        session.setAttribute("canEditUserManagement", canEditUserManagement);
        session.setAttribute("canViewLicenseManagement", canViewLicenseManagement);
        session.setAttribute("canEditLicenseManagement", RoleAccess.canEditPage(role, editPermissions, RoleAccess.PAGE_LICENSE_MANAGEMENT));
        session.setAttribute("canViewEmailConfiguration", canViewEmailConfiguration);
        session.setAttribute("canEditEmailConfiguration", RoleAccess.canEditPage(role, editPermissions, RoleAccess.PAGE_EMAIL_CONFIGURATION));
        session.setAttribute("canViewConfigurationSettingsGroup",
                canViewIssueBoardConfiguration || canViewGembaWalkConfiguration || canViewLeadershipGembaTrackerConfiguration
                        || canViewTrainingScheduleConfiguration || canViewMeetingAgendaConfiguration
                        || canViewProcessConfirmationConfiguration || canViewAbnormalityTrackerConfiguration);
        session.setAttribute("canViewProductionSettingsGroup",
                canViewProductionMetricsData || canViewHsCrossDailyConfiguration || canViewLsrTrackingConfiguration);
        session.setAttribute("canViewKpiConfigurationGroup", canViewKpiTargetCrossColor || canViewKpiRenameDashboard || canViewKpiPlantName);
        session.setAttribute("canViewKpiDashboard",
                RoleAccess.isAdmin(role) || canViewPmsDataEntry || canViewProductionMetricsData);
    }

    private String landingPageForSession(HttpSession session) {
        if (session == null) {
            return "/pms-login";
        }
        return landingPageFor((String) session.getAttribute("role"), toPermissionSet(session.getAttribute("viewPermissions")));
    }

    private String landingPageFor(String role, Set<String> viewPermissions) {
        if (RoleAccess.isAdmin(role)) {
            return "/kpi-dashboard";
        }
        Set<String> pages = RoleAccess.sanitizePages(viewPermissions);
        if (pages.contains(RoleAccess.PAGE_PMS_DATA_ENTRY)
                || pages.contains(RoleAccess.PAGE_PRODUCTION_METRICS_DATA)
                || pages.contains(RoleAccess.PAGE_PRODUCTION_METRICS_DATA_PEOPLE)
                || pages.contains(RoleAccess.PAGE_PRODUCTION_METRICS_DATA_QUALITY)
                || pages.contains(RoleAccess.PAGE_PRODUCTION_METRICS_DATA_SERVICE)
                || pages.contains(RoleAccess.PAGE_PRODUCTION_METRICS_DATA_COST)) {
            return "/kpi-dashboard";
        }
        if (pages.contains(RoleAccess.PAGE_ISSUE_BOARD_CONFIGURATION)) {
            return "/issue-board";
        }
        if (pages.contains(RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION)) {
            return "/gemba-schedule";
        }
        if (pages.contains(RoleAccess.PAGE_TRAINING_SCHEDULE_CONFIGURATION)) {
            return "/training-schedule";
        }
        if (pages.contains(RoleAccess.PAGE_MEETING_AGENDA_CONFIGURATION)) {
            return "/meeting-agenda";
        }
        if (pages.contains(RoleAccess.PAGE_ABNORMALITY_TRACKER_CONFIGURATION)) {
            return "/abnormality-tracker";
        }
        if (pages.contains(RoleAccess.PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION)) {
            return "/leadership-gemba-tracker";
        }
        if (pages.contains(RoleAccess.PAGE_PROCESS_CONFIRMATION_CONFIGURATION)) {
            return "/process-confirmation";
        }
        if (pages.contains(RoleAccess.PAGE_INFO_PORTAL)) {
            return "/client-selection";
        }
        if (pages.contains(RoleAccess.PAGE_USER_MANAGEMENT)) {
            return "/pms-configuration";
        }
        if (pages.contains(RoleAccess.PAGE_LICENSE_MANAGEMENT)) {
            return "/settings?config=license";
        }
        if (pages.contains(RoleAccess.PAGE_EMAIL_CONFIGURATION)) {
            return "/email-configuration";
        }
        if (pages.contains(RoleAccess.PAGE_HS_CROSS_DAILY_CONFIGURATION)) {
            return "/settings?config=hs-cross";
        }
        if (pages.contains(RoleAccess.PAGE_LSR_TRACKING_CONFIGURATION)) {
            return "/settings?config=lsr-tracking";
        }
        if (pages.contains(RoleAccess.PAGE_KPI_TARGET_CROSS_COLOR)) {
            return "/settings?config=kpi-rename-dashboard";
        }
        if (pages.contains(RoleAccess.PAGE_KPI_RENAME_DASHBOARD)) {
            return "/settings?config=kpi-rename-dashboard";
        }
        if (pages.contains(RoleAccess.PAGE_KPI_PLANT_NAME)) {
            return "/settings?config=kpi-rename-dashboard";
        }
        return "/kpi-dashboard";
    }

    private Map<String, Object> toUserResponse(AppUser user) {
        String normalizedRole = RoleAccess.normalize(user.getRole());
        Set<String> viewPermissions = toPermissionSet(user.getPageViewPermissions());
        Set<String> editPermissions = toPermissionSet(user.getPageEditPermissions());
        if (RoleAccess.isAdmin(normalizedRole)) {
            viewPermissions = RoleAccess.CONFIG_PAGES;
            editPermissions = RoleAccess.CONFIG_PAGES;
        }

        Map<String, Object> row = new HashMap<>();
        row.put("id", user.getId());
        row.put("username", user.getUsername());
        row.put("email", user.getEmail());
        row.put("role", normalizedRole);
        row.put("roleLabel", RoleAccess.displayName(user.getRole()));
        row.put("viewPermissions", viewPermissions);
        row.put("editPermissions", editPermissions);
        row.put("status", "Active");
        return row;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Set<String> toPermissionSet(Object raw) {
        if (raw == null) {
            return Set.of();
        }

        Set<String> values = new HashSet<>();
        if (raw instanceof String stringValue) {
            for (String item : stringValue.split(",")) {
                if (item != null && !item.isBlank()) {
                    values.add(item.trim());
                }
            }
            return RoleAccess.sanitizePages(values);
        }

        if (raw instanceof java.util.Collection<?> collectionValue) {
            for (Object item : collectionValue) {
                if (item != null) {
                    String value = String.valueOf(item).trim();
                    if (!value.isBlank()) {
                        values.add(value);
                    }
                }
            }
        }

        return RoleAccess.sanitizePages(values);
    }
}
