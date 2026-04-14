package org.example.util;

import java.util.Locale;

public final class RoleAccess {

    public static final String ADMIN = "ADMIN";
    public static final String L1_USER = "L1_USER";
    public static final String L2_USER = "L2_USER";
    public static final String LEGACY_USER = "USER";

    private RoleAccess() {
    }

    public static String normalize(String role) {
        if (role == null) {
            return "";
        }

        String normalized = role.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (normalized) {
            case ADMIN -> ADMIN;
            case LEGACY_USER, "L1", L1_USER -> L1_USER;
            case "L2", L2_USER -> L2_USER;
            default -> normalized;
        };
    }

    public static boolean isSupported(String role) {
        String normalized = normalize(role);
        return ADMIN.equals(normalized) || L1_USER.equals(normalized) || L2_USER.equals(normalized);
    }

    public static boolean isAdmin(String role) {
        return ADMIN.equals(normalize(role));
    }

    public static boolean canAccessPmsDataEntry(String role) {
        String normalized = normalize(role);
        return ADMIN.equals(normalized) || L1_USER.equals(normalized);
    }

    public static boolean canAccessEmailConfiguration(String role) {
        String normalized = normalize(role);
        return ADMIN.equals(normalized) || L1_USER.equals(normalized);
    }

    public static String displayName(String role) {
        return switch (normalize(role)) {
            case ADMIN -> "Admin";
            case L1_USER -> "L1 User";
            case L2_USER -> "L2 User";
            default -> role == null || role.isBlank() ? "User" : role;
        };
    }
}
