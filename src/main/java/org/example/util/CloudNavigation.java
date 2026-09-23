package org.example.util;

import jakarta.servlet.http.HttpSession;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The cloud UI surface. API permissions remain enforced by AuthInterceptor. */
public final class CloudNavigation {
    private CloudNavigation() {}

    public record Page(String href, String title, String permission, boolean master) {}

    private static final List<Page> PAGES = List.of(
        new Page("/settings?config=kpi-plant-name", "Plant", RoleAccess.PAGE_KPI_PLANT_NAME, true),
        new Page("/gemba-walk-config", "Gemba Walk Reporting", RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION, false),
        new Page("/gemba-kaizen-config", "Gemba Kaizen Reporting", RoleAccess.PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION, false),
        new Page("/process-confirmation-config", "CarlEx Process Confirmation", RoleAccess.PAGE_PROCESS_CONFIRMATION_CONFIGURATION, false),
        new Page("/abnormality-reporting-config", "Abnormality Reporting", RoleAccess.PAGE_ABNORMALITY_TRACKER_CONFIGURATION, false),
        new Page("/pms-configuration", "User Management", RoleAccess.PAGE_USER_MANAGEMENT, true),
        new Page("/sync-configuration", "Cloud Sync Configuration", "sync", true),
        new Page("/email-configuration", "Email Scheduler", RoleAccess.PAGE_EMAIL_CONFIGURATION, true),
        new Page("/settings?config=master-abnormality", "Abnormality", RoleAccess.PAGE_ABNORMALITY_TRACKER_CONFIGURATION, true),
        new Page("/settings?config=master-gemba-walk", "Gemba Walk", RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION, true),
        new Page("/settings?config=master-gemba-kaizen", "Gemba Kaizen", RoleAccess.PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION, true),
        new Page("/settings?config=master-process", "Process", RoleAccess.PAGE_PROCESS_CONFIRMATION_CONFIGURATION, true),
        new Page("/settings?config=master-designation", "Designation", RoleAccess.PAGE_KPI_PLANT_NAME, true),
        new Page("/smtp-configuration", "SMTP Config", RoleAccess.PAGE_EMAIL_CONFIGURATION, true),
        new Page("/settings?config=license", "License Management", RoleAccess.PAGE_LICENSE_MANAGEMENT, true)
    );

    public static List<Page> pages(HttpSession session, boolean syncUser) {
        if (session == null) return List.of();
        String role = (String) session.getAttribute("role");
        Set<String> view = permissions(session, "viewPermissions");
        Set<String> edit = permissions(session, "editPermissions");
        return PAGES.stream().filter(page -> page.permission().equals("sync") ? syncUser
            : RoleAccess.canViewPage(role, view, page.permission())
                || RoleAccess.canEditPage(role, edit, page.permission())).toList();
    }

    public static String landing(HttpSession session, boolean syncUser) {
        return pages(session, syncUser).stream().map(Page::href).findFirst().orElse("/cloud-no-access");
    }

    public static boolean isRetainedPage(String path, String config) {
        if (path.equals("/cloud-no-access")) return true;
        String href = path.equals("/settings") ? path + "?config=" + config : path;
        return PAGES.stream().anyMatch(page -> page.href().equals(href));
    }

    private static Set<String> permissions(HttpSession session, String name) {
        Object value = session.getAttribute(name);
        if (!(value instanceof Collection<?> values)) return Set.of();
        return values.stream().filter(java.util.Objects::nonNull).map(Object::toString).collect(Collectors.toSet());
    }
}
