package org.example.util;

import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class RoleAccess {

    public static final String ADMIN = "ADMIN";
    public static final String USER = "USER";

        public static final String LEGACY_PAGE_SETTINGS = "SETTINGS";
        public static final String PAGE_PMS_DATA_ENTRY = "PMS_DATA_ENTRY";
        public static final String PAGE_PRODUCTION_METRICS_DATA = "PRODUCTION_METRICS_DATA";
        public static final String PAGE_ISSUE_BOARD_CONFIGURATION = "ISSUE_BOARD_CONFIGURATION";
        public static final String PAGE_GEMBA_WALK_CONFIGURATION = "GEMBA_WALK_CONFIGURATION";
        public static final String PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION = "LEADERSHIP_GEMBA_TRACKER_CONFIGURATION";
        public static final String PAGE_TRAINING_SCHEDULE_CONFIGURATION = "TRAINING_SCHEDULE_CONFIGURATION";
        public static final String PAGE_MEETING_AGENDA_CONFIGURATION = "MEETING_AGENDA_CONFIGURATION";
        public static final String PAGE_PROCESS_CONFIRMATION_CONFIGURATION = "PROCESS_CONFIRMATION_CONFIGURATION";
        public static final String PAGE_ABNORMALITY_TRACKER_CONFIGURATION = "ABNORMALITY_TRACKER_CONFIGURATION";
        public static final String PAGE_HS_CROSS_DAILY_CONFIGURATION = "HS_CROSS_DAILY_CONFIGURATION";
        public static final String PAGE_LSR_TRACKING_CONFIGURATION = "LSR_TRACKING_CONFIGURATION";
        public static final String PAGE_KPI_FOOTER_BUTTONS = "KPI_FOOTER_BUTTONS";
        public static final String PAGE_KPI_TARGET_CROSS_COLOR = "KPI_TARGET_CROSS_COLOR";
        public static final String PAGE_USER_MANAGEMENT = "USER_MANAGEMENT";
        public static final String PAGE_LICENSE_MANAGEMENT = "LICENSE_MANAGEMENT";
    public static final String PAGE_EMAIL_CONFIGURATION = "EMAIL_CONFIGURATION";

        public static final Set<String> CONFIGURATION_NAV_PAGES = Set.of(
            PAGE_PMS_DATA_ENTRY,
            PAGE_PRODUCTION_METRICS_DATA,
            PAGE_ISSUE_BOARD_CONFIGURATION,
            PAGE_GEMBA_WALK_CONFIGURATION,
            PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION,
            PAGE_TRAINING_SCHEDULE_CONFIGURATION,
            PAGE_MEETING_AGENDA_CONFIGURATION,
            PAGE_PROCESS_CONFIRMATION_CONFIGURATION,
            PAGE_ABNORMALITY_TRACKER_CONFIGURATION,
            PAGE_HS_CROSS_DAILY_CONFIGURATION,
            PAGE_LSR_TRACKING_CONFIGURATION,
            PAGE_KPI_FOOTER_BUTTONS,
            PAGE_KPI_TARGET_CROSS_COLOR
        );

        public static final Set<String> LEGACY_SETTINGS_EQUIVALENT_PAGES = Set.of(
            PAGE_PMS_DATA_ENTRY,
            PAGE_PRODUCTION_METRICS_DATA,
            PAGE_ISSUE_BOARD_CONFIGURATION,
            PAGE_GEMBA_WALK_CONFIGURATION,
            PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION,
            PAGE_TRAINING_SCHEDULE_CONFIGURATION,
            PAGE_MEETING_AGENDA_CONFIGURATION,
            PAGE_PROCESS_CONFIRMATION_CONFIGURATION,
            PAGE_ABNORMALITY_TRACKER_CONFIGURATION,
            PAGE_HS_CROSS_DAILY_CONFIGURATION,
            PAGE_LSR_TRACKING_CONFIGURATION,
            PAGE_KPI_FOOTER_BUTTONS,
            PAGE_KPI_TARGET_CROSS_COLOR
        );

        public static final Set<String> CONFIG_PAGES = Set.of(
            PAGE_PMS_DATA_ENTRY,
            PAGE_PRODUCTION_METRICS_DATA,
            PAGE_ISSUE_BOARD_CONFIGURATION,
            PAGE_GEMBA_WALK_CONFIGURATION,
            PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION,
            PAGE_TRAINING_SCHEDULE_CONFIGURATION,
            PAGE_MEETING_AGENDA_CONFIGURATION,
            PAGE_PROCESS_CONFIRMATION_CONFIGURATION,
            PAGE_ABNORMALITY_TRACKER_CONFIGURATION,
            PAGE_HS_CROSS_DAILY_CONFIGURATION,
            PAGE_LSR_TRACKING_CONFIGURATION,
            PAGE_KPI_FOOTER_BUTTONS,
            PAGE_KPI_TARGET_CROSS_COLOR,
            PAGE_USER_MANAGEMENT,
            PAGE_LICENSE_MANAGEMENT,
            PAGE_EMAIL_CONFIGURATION
        );

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

        Set<String> sanitized = new HashSet<>();
        for (String page : pages) {
            if (page == null || page.isBlank()) {
                continue;
            }

            String normalized = page.trim().toUpperCase(Locale.ROOT);
            if (LEGACY_PAGE_SETTINGS.equals(normalized)) {
                sanitized.addAll(LEGACY_SETTINGS_EQUIVALENT_PAGES);
                continue;
            }

            if (CONFIG_PAGES.contains(normalized)) {
                sanitized.add(normalized);
            }
        }

        return Set.copyOf(sanitized);
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

    public static boolean canViewAnyConfigurationPage(String role, Set<String> viewPages) {
        if (isAdmin(role)) {
            return true;
        }
        if (!USER.equals(normalize(role))) {
            return false;
        }
        return sanitizePages(viewPages).stream().anyMatch(CONFIGURATION_NAV_PAGES::contains);
    }

    public static boolean canEditAnyConfigurationPage(String role, Set<String> editPages) {
        if (isAdmin(role)) {
            return true;
        }
        if (!USER.equals(normalize(role))) {
            return false;
        }
        return sanitizePages(editPages).stream().anyMatch(CONFIGURATION_NAV_PAGES::contains);
    }

    public static String pageKeyForSettingsConfig(String config) {
        if (config == null || config.isBlank()) {
            return "";
        }

        return switch (config.trim().toLowerCase(Locale.ROOT)) {
            case "metrics-data" -> PAGE_PRODUCTION_METRICS_DATA;
            case "issue-board" -> PAGE_ISSUE_BOARD_CONFIGURATION;
            case "gemba-schedule" -> PAGE_GEMBA_WALK_CONFIGURATION;
            case "leadership-gemba-tracker" -> PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION;
            case "training-schedule" -> PAGE_TRAINING_SCHEDULE_CONFIGURATION;
            case "meeting-agenda" -> PAGE_MEETING_AGENDA_CONFIGURATION;
            case "process-confirmation" -> PAGE_PROCESS_CONFIRMATION_CONFIGURATION;
            case "abnormality-tracker" -> PAGE_ABNORMALITY_TRACKER_CONFIGURATION;
            case "hs-cross" -> PAGE_HS_CROSS_DAILY_CONFIGURATION;
            case "lsr-tracking" -> PAGE_LSR_TRACKING_CONFIGURATION;
            case "kpi-footer-buttons" -> PAGE_KPI_FOOTER_BUTTONS;
            case "kpi-cross-color" -> PAGE_KPI_TARGET_CROSS_COLOR;
            case "license" -> PAGE_LICENSE_MANAGEMENT;
            default -> "";
        };
    }

    public static String pageKeyForActivePage(String activePage) {
        if (activePage == null || activePage.isBlank()) {
            return "";
        }

        return switch (activePage.trim().toLowerCase(Locale.ROOT)) {
            case "priorities", "weekly-priorities", "daily-performance", "daily-section" -> PAGE_PMS_DATA_ENTRY;
            case "metrics-data" -> PAGE_PRODUCTION_METRICS_DATA;
            case "issue-board" -> PAGE_ISSUE_BOARD_CONFIGURATION;
            case "gemba-schedule" -> PAGE_GEMBA_WALK_CONFIGURATION;
            case "leadership-gemba-tracker" -> PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION;
            case "training-schedule" -> PAGE_TRAINING_SCHEDULE_CONFIGURATION;
            case "meeting-agenda" -> PAGE_MEETING_AGENDA_CONFIGURATION;
            case "process-confirmation" -> PAGE_PROCESS_CONFIRMATION_CONFIGURATION;
            case "abnormality-tracker" -> PAGE_ABNORMALITY_TRACKER_CONFIGURATION;
            case "hs-cross" -> PAGE_HS_CROSS_DAILY_CONFIGURATION;
            case "lsr-tracking" -> PAGE_LSR_TRACKING_CONFIGURATION;
            case "kpi-footer-buttons" -> PAGE_KPI_FOOTER_BUTTONS;
            case "kpi-cross-color" -> PAGE_KPI_TARGET_CROSS_COLOR;
            case "license" -> PAGE_LICENSE_MANAGEMENT;
            default -> "";
        };
    }

    public static String displayName(String role) {
        return switch (normalize(role)) {
            case ADMIN -> "Admin";
            case USER -> "User";
            default -> role == null || role.isBlank() ? "User" : role;
        };
    }
}
