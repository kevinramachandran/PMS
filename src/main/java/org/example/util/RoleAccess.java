package org.example.util;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class RoleAccess {

    public static final String ADMIN = "ADMIN";
    public static final String USER = "USER";

    public static final String PAGE_SETTINGS = "SETTINGS";
    public static final String PAGE_EMAIL_CONFIGURATION = "EMAIL_CONFIGURATION";
    public static final Set<String> CONFIG_PAGES = Set.of(PAGE_SETTINGS, PAGE_EMAIL_CONFIGURATION);

    private RoleAccess() {
    }

    public static String normalize(String role) {
        if (role == null) {
            return "";
        }

        String normalized = role.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (normalized) {
            case ADMIN -> ADMIN;
            case USER, "L1", "L1_USER", "L2", "L2_USER" -> USER;
            default -> normalized;
        };
    }

    public static boolean isSupported(String role) {
        String normalized = normalize(role);
        return ADMIN.equals(normalized) || USER.equals(normalized);
    }

    public static boolean isAdmin(String role) {
        return ADMIN.equals(normalize(role));
    }

    public static Set<String> sanitizePages(Set<String> pages) {
        if (pages == null || pages.isEmpty()) {
            return Set.of();
        }
        return pages.stream()
                .filter(p -> p != null && !p.isBlank())
                .map(p -> p.trim().toUpperCase(Locale.ROOT))
                .filter(CONFIG_PAGES::contains)
                .collect(Collectors.toSet());
    }

    public static boolean canViewPage(String role, Set<String> viewPages, String pageKey) {
        if (isAdmin(role)) {
            return true;
        }
        if (!USER.equals(normalize(role))) {
            return false;
        }
        return sanitizePages(viewPages).contains(pageKey);
    }

    public static boolean canEditPage(String role, Set<String> editPages, String pageKey) {
        if (isAdmin(role)) {
            return true;
        }
        if (!USER.equals(normalize(role))) {
            return false;
        }
        return sanitizePages(editPages).contains(pageKey);
    }

    public static String displayName(String role) {
        return switch (normalize(role)) {
            case ADMIN -> "Admin";
            case USER -> "User";
            default -> role == null || role.isBlank() ? "User" : role;
        };
    }
}
