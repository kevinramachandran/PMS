package org.example.util;

import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class RoleAccess {

    public static final String ADMIN = "ADMIN";
    public static final String USER = "USER";
    public static final String HOD = "HOD";
    public static final String AREA_HOD = "AREA_HOD";
    public static final String ENGINEER = "ENGINEER";
    public static final String EXECUTIVE = "EXECUTIVE";
    public static final String OPERATOR = "OPERATOR";
    public static final Set<String> STANDARD_USER_ROLES = Set.of(
            USER,
            HOD,
            AREA_HOD,
            ENGINEER,
            EXECUTIVE,
            OPERATOR
    );

        public static final String LEGACY_PAGE_SETTINGS = "SETTINGS";
        public static final String PAGE_PMS_DATA_ENTRY = "PMS_DATA_ENTRY";
        public static final String PAGE_PRODUCTION_METRICS_DATA = "PRODUCTION_METRICS_DATA";
        public static final String PAGE_PRODUCTION_METRICS_DATA_PEOPLE = "PRODUCTION_METRICS_DATA_PEOPLE";
        public static final String PAGE_PRODUCTION_METRICS_DATA_QUALITY = "PRODUCTION_METRICS_DATA_QUALITY";
        public static final String PAGE_PRODUCTION_METRICS_DATA_SERVICE = "PRODUCTION_METRICS_DATA_SERVICE";
        public static final String PAGE_PRODUCTION_METRICS_DATA_COST = "PRODUCTION_METRICS_DATA_COST";
        public static final String PAGE_ISSUE_BOARD_CONFIGURATION = "ISSUE_BOARD_CONFIGURATION";
        public static final String PAGE_GEMBA_WALK_CONFIGURATION = "GEMBA_WALK_CONFIGURATION";
        public static final String PAGE_GEMBA_WALK_FINDINGS = "GEMBA_WALK_FINDINGS";
        public static final String PAGE_GEMBA_WALK_REPORTING = "GEMBA_WALK_REPORTING";
        public static final String PAGE_USER_DASHBOARD = "USER_DASHBOARD";
        public static final String PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION = "LEADERSHIP_GEMBA_TRACKER_CONFIGURATION";
        public static final String PAGE_TRAINING_SCHEDULE_CONFIGURATION = "TRAINING_SCHEDULE_CONFIGURATION";
        public static final String PAGE_MEETING_AGENDA_CONFIGURATION = "MEETING_AGENDA_CONFIGURATION";
        public static final String PAGE_PROCESS_CONFIRMATION_CONFIGURATION = "PROCESS_CONFIRMATION_CONFIGURATION";
        public static final String PAGE_ABNORMALITY_TRACKER_CONFIGURATION = "ABNORMALITY_TRACKER_CONFIGURATION";
        public static final String PAGE_HS_CROSS_DAILY_CONFIGURATION = "HS_CROSS_DAILY_CONFIGURATION";
        public static final String PAGE_LSR_TRACKING_CONFIGURATION = "LSR_TRACKING_CONFIGURATION";
        public static final String PAGE_INFO_PORTAL = "INFO_PORTAL";
        public static final String PAGE_KPI_TARGET_CROSS_COLOR = "KPI_TARGET_CROSS_COLOR";
        public static final String PAGE_KPI_RENAME_DASHBOARD = "KPI_RENAME_DASHBOARD";
        public static final String PAGE_KPI_PLANT_NAME = "KPI_PLANT_NAME";
        public static final String PAGE_USER_MANAGEMENT = "USER_MANAGEMENT";
        public static final String PAGE_LICENSE_MANAGEMENT = "LICENSE_MANAGEMENT";
    public static final String PAGE_EMAIL_CONFIGURATION = "EMAIL_CONFIGURATION";

        public static final Set<String> CONFIGURATION_NAV_PAGES = Set.of(
            PAGE_PMS_DATA_ENTRY,
            PAGE_PRODUCTION_METRICS_DATA,
            PAGE_PRODUCTION_METRICS_DATA_PEOPLE,
            PAGE_PRODUCTION_METRICS_DATA_QUALITY,
            PAGE_PRODUCTION_METRICS_DATA_SERVICE,
            PAGE_PRODUCTION_METRICS_DATA_COST,
            PAGE_ISSUE_BOARD_CONFIGURATION,
            PAGE_GEMBA_WALK_CONFIGURATION,
            PAGE_GEMBA_WALK_FINDINGS,
            PAGE_GEMBA_WALK_REPORTING,
            PAGE_USER_DASHBOARD,
            PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION,
            PAGE_TRAINING_SCHEDULE_CONFIGURATION,
            PAGE_MEETING_AGENDA_CONFIGURATION,
            PAGE_PROCESS_CONFIRMATION_CONFIGURATION,
            PAGE_ABNORMALITY_TRACKER_CONFIGURATION,
            PAGE_HS_CROSS_DAILY_CONFIGURATION,
            PAGE_LSR_TRACKING_CONFIGURATION,
            PAGE_INFO_PORTAL,
            PAGE_KPI_TARGET_CROSS_COLOR,
            PAGE_KPI_RENAME_DASHBOARD,
            PAGE_KPI_PLANT_NAME
        );

        public static final Set<String> LEGACY_SETTINGS_EQUIVALENT_PAGES = Set.of(
            PAGE_PMS_DATA_ENTRY,
            PAGE_PRODUCTION_METRICS_DATA,
            PAGE_ISSUE_BOARD_CONFIGURATION,
            PAGE_GEMBA_WALK_CONFIGURATION,
            PAGE_GEMBA_WALK_FINDINGS,
            PAGE_GEMBA_WALK_REPORTING,
            PAGE_USER_DASHBOARD,
            PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION,
            PAGE_TRAINING_SCHEDULE_CONFIGURATION,
            PAGE_MEETING_AGENDA_CONFIGURATION,
            PAGE_PROCESS_CONFIRMATION_CONFIGURATION,
            PAGE_ABNORMALITY_TRACKER_CONFIGURATION,
            PAGE_HS_CROSS_DAILY_CONFIGURATION,
            PAGE_LSR_TRACKING_CONFIGURATION,
            PAGE_INFO_PORTAL,
            PAGE_KPI_TARGET_CROSS_COLOR,
            PAGE_KPI_RENAME_DASHBOARD,
            PAGE_KPI_PLANT_NAME
        );

        public static final Set<String> CONFIG_PAGES = Set.of(
            PAGE_PMS_DATA_ENTRY,
            PAGE_PRODUCTION_METRICS_DATA,
            PAGE_PRODUCTION_METRICS_DATA_PEOPLE,
            PAGE_PRODUCTION_METRICS_DATA_QUALITY,
            PAGE_PRODUCTION_METRICS_DATA_SERVICE,
            PAGE_PRODUCTION_METRICS_DATA_COST,
            PAGE_ISSUE_BOARD_CONFIGURATION,
            PAGE_GEMBA_WALK_CONFIGURATION,
            PAGE_GEMBA_WALK_FINDINGS,
            PAGE_GEMBA_WALK_REPORTING,
            PAGE_USER_DASHBOARD,
            PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION,
            PAGE_TRAINING_SCHEDULE_CONFIGURATION,
            PAGE_MEETING_AGENDA_CONFIGURATION,
            PAGE_PROCESS_CONFIRMATION_CONFIGURATION,
            PAGE_ABNORMALITY_TRACKER_CONFIGURATION,
            PAGE_HS_CROSS_DAILY_CONFIGURATION,
            PAGE_LSR_TRACKING_CONFIGURATION,
            PAGE_INFO_PORTAL,
            PAGE_KPI_TARGET_CROSS_COLOR,
            PAGE_KPI_RENAME_DASHBOARD,
            PAGE_KPI_PLANT_NAME,
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
            case HOD, "HO_D", "HEAD_OF_DEPARTMENT", "DEPARTMENT_HEAD" -> HOD;
            case AREA_HOD, "AREA_HEAD", "AREA_HEAD_OF_DEPARTMENT" -> AREA_HOD;
            case ENGINEER, "ENGG" -> ENGINEER;
            case EXECUTIVE, "EXEC" -> EXECUTIVE;
            case OPERATOR -> OPERATOR;
            default -> normalized;
        };
    }

    public static boolean isSupported(String role) {
        String normalized = normalize(role);
        return ADMIN.equals(normalized) || STANDARD_USER_ROLES.contains(normalized);
    }

    public static boolean isAdmin(String role) {
        return ADMIN.equals(normalize(role));
    }

    public static boolean isHod(String role) {
        String normalized = normalize(role);
        return HOD.equals(normalized) || AREA_HOD.equals(normalized);
    }

    public static boolean isAssignableOperationalRole(String role) {
        String normalized = normalize(role);
        return ENGINEER.equals(normalized) || EXECUTIVE.equals(normalized) || OPERATOR.equals(normalized);
    }

    public static boolean isStandardUserRole(String role) {
        return STANDARD_USER_ROLES.contains(normalize(role));
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

    private static final Set<String> PRODUCTION_METRICS_PAGE_KEYS = Set.of(
        PAGE_PRODUCTION_METRICS_DATA,
        PAGE_PRODUCTION_METRICS_DATA_PEOPLE,
        PAGE_PRODUCTION_METRICS_DATA_QUALITY,
        PAGE_PRODUCTION_METRICS_DATA_SERVICE,
        PAGE_PRODUCTION_METRICS_DATA_COST
    );

    public static boolean canViewPage(String role, Set<String> viewPages, String pageKey) {
        if (isAdmin(role)) {
            return true;
        }
        if (!isStandardUserRole(role)) {
            return false;
        }

        Set<String> sanitized = sanitizePages(viewPages);
        if (PAGE_PRODUCTION_METRICS_DATA.equals(pageKey)) {
            return sanitized.stream().anyMatch(PRODUCTION_METRICS_PAGE_KEYS::contains);
        }
        if (PAGE_KPI_RENAME_DASHBOARD.equals(pageKey)) {
            return sanitized.contains(PAGE_KPI_RENAME_DASHBOARD)
                    || sanitized.contains(PAGE_KPI_TARGET_CROSS_COLOR);
        }

        return sanitized.contains(pageKey);
    }

    public static boolean canEditPage(String role, Set<String> editPages, String pageKey) {
        if (isAdmin(role)) {
            return true;
        }
        if (!isStandardUserRole(role)) {
            return false;
        }

        Set<String> sanitized = sanitizePages(editPages);
        if (PAGE_PRODUCTION_METRICS_DATA.equals(pageKey)) {
            return sanitized.stream().anyMatch(PRODUCTION_METRICS_PAGE_KEYS::contains);
        }
        if (PAGE_KPI_RENAME_DASHBOARD.equals(pageKey)) {
            return sanitized.contains(PAGE_KPI_RENAME_DASHBOARD)
                    || sanitized.contains(PAGE_KPI_TARGET_CROSS_COLOR);
        }

        return sanitized.contains(pageKey);
    }

    public static boolean canViewAnyConfigurationPage(String role, Set<String> viewPages) {
        if (isAdmin(role)) {
            return true;
        }
        if (!isStandardUserRole(role)) {
            return false;
        }
        return sanitizePages(viewPages).stream().anyMatch(CONFIGURATION_NAV_PAGES::contains);
    }

    public static boolean canEditAnyConfigurationPage(String role, Set<String> editPages) {
        if (isAdmin(role)) {
            return true;
        }
        if (!isStandardUserRole(role)) {
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
            case "master-gemba-walk" -> PAGE_GEMBA_WALK_CONFIGURATION;
            case "gemba-schedule" -> PAGE_GEMBA_WALK_CONFIGURATION;
            case "gemba-findings" -> PAGE_GEMBA_WALK_FINDINGS;
            case "gemba-reporting" -> PAGE_GEMBA_WALK_REPORTING;
            case "user-dashboard" -> PAGE_USER_DASHBOARD;
            case "master-gemba-kaizen" -> PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION;
            case "leadership-gemba-tracker" -> PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION;
            case "training-schedule" -> PAGE_TRAINING_SCHEDULE_CONFIGURATION;
            case "meeting-agenda" -> PAGE_MEETING_AGENDA_CONFIGURATION;
            case "master-process" -> PAGE_PROCESS_CONFIRMATION_CONFIGURATION;
            case "process-confirmation" -> PAGE_PROCESS_CONFIRMATION_CONFIGURATION;
            case "master-abnormality" -> PAGE_ABNORMALITY_TRACKER_CONFIGURATION;
            case "abnormality-tracker" -> PAGE_ABNORMALITY_TRACKER_CONFIGURATION;
            case "hs-cross" -> PAGE_HS_CROSS_DAILY_CONFIGURATION;
            case "lsr-tracking" -> PAGE_LSR_TRACKING_CONFIGURATION;
            case "info-portal" -> PAGE_INFO_PORTAL;
            case "kpi-cross-color" -> PAGE_KPI_TARGET_CROSS_COLOR;
            case "kpi-rename-dashboard" -> PAGE_KPI_RENAME_DASHBOARD;
            case "kpi-plant-name" -> PAGE_KPI_PLANT_NAME;
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
            case "master-gemba-walk" -> PAGE_GEMBA_WALK_CONFIGURATION;
            case "gemba-schedule" -> PAGE_GEMBA_WALK_CONFIGURATION;
            case "gemba-findings" -> PAGE_GEMBA_WALK_FINDINGS;
            case "gemba-reporting" -> PAGE_GEMBA_WALK_REPORTING;
            case "user-dashboard" -> PAGE_USER_DASHBOARD;
            case "master-gemba-kaizen" -> PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION;
            case "leadership-gemba-tracker" -> PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION;
            case "training-schedule" -> PAGE_TRAINING_SCHEDULE_CONFIGURATION;
            case "meeting-agenda" -> PAGE_MEETING_AGENDA_CONFIGURATION;
            case "master-process" -> PAGE_PROCESS_CONFIRMATION_CONFIGURATION;
            case "process-confirmation" -> PAGE_PROCESS_CONFIRMATION_CONFIGURATION;
            case "master-abnormality" -> PAGE_ABNORMALITY_TRACKER_CONFIGURATION;
            case "abnormality-tracker" -> PAGE_ABNORMALITY_TRACKER_CONFIGURATION;
            case "hs-cross" -> PAGE_HS_CROSS_DAILY_CONFIGURATION;
            case "lsr-tracking" -> PAGE_LSR_TRACKING_CONFIGURATION;
            case "info-portal" -> PAGE_INFO_PORTAL;
            case "kpi-cross-color" -> PAGE_KPI_TARGET_CROSS_COLOR;
            case "kpi-rename-dashboard" -> PAGE_KPI_RENAME_DASHBOARD;
            case "kpi-plant-name" -> PAGE_KPI_PLANT_NAME;
            case "license" -> PAGE_LICENSE_MANAGEMENT;
            default -> "";
        };
    }

    public static String displayName(String role) {
        return switch (normalize(role)) {
            case ADMIN -> "Admin";
            case USER -> "User";
            case HOD -> "HoD";
            case AREA_HOD -> "Area HoD";
            case ENGINEER -> "Engineer";
            case EXECUTIVE -> "Executive";
            case OPERATOR -> "Operator";
            default -> role == null || role.isBlank() ? "User" : role;
        };
    }
}
