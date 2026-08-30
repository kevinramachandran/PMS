package org.example.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.service.AuthService;
import org.example.service.LicenseService;
import org.example.util.RoleAccess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private AuthService authService;

    @Autowired
    private LicenseService licenseService;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                              @NonNull HttpServletResponse response,
                              @NonNull Object handler) throws Exception {

        HttpSession session  = request.getSession(false);
        String username      = (session != null) ? (String) session.getAttribute("username") : null;
        String role          = (session != null) ? (String) session.getAttribute("role")     : null;

        String path = request.getRequestURI();

        // Not logged in: APIs must return 401 JSON (not HTML redirect), pages redirect to login.
        if (username == null) {
            // /api/license/generate is open — no auth required
            if (path.equals("/api/license/generate")) {
                return true;
            }

            if (path.startsWith("/api/") && !path.startsWith("/api/auth/")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":\"error\",\"message\":\"Unauthorized\"}");
                return false;
            }

            response.sendRedirect(request.getContextPath() + "/pms-login");
            return false;
        }

        boolean licenseBypassUser = authService.isLicenseBypassUser(username);

        // /api/license/generate is open — skip license gate
        if (!path.equals("/api/license/generate")) {
            LicenseService.LicenseGateResult gate = licenseService.evaluateForLogin(licenseBypassUser);
            if (!gate.allowed()) {
                if (session != null) {
                    session.invalidate();
                }

                if (path.startsWith("/api/")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":\"error\",\"message\":\"" + gate.message() + "\",\"code\":\"" + gate.code() + "\"}");
                } else {
                    response.sendRedirect(request.getContextPath() + "/pms-login?licenseBlocked=" + gate.code());
                }
                return false;
            }
        }

        if (RoleAccess.isAdmin(role)) {
            applyNavigationSessionAttributes(session, role, RoleAccess.CONFIG_PAGES, RoleAccess.CONFIG_PAGES);
            applyPermissionAttributes(request, true, true, protectedPageKeyForRequest(request));
            return true;
        }

        Set<String> viewPermissions = extractPermissions(session, "viewPermissions");
        Set<String> editPermissions = extractPermissions(session, "editPermissions");
        applyNavigationSessionAttributes(session, role, viewPermissions, editPermissions);
        String protectedPageKey = resolveProtectedPageKey(request);
        boolean canViewCurrentPage = protectedPageKey == null || protectedPageKey.isBlank()
                || RoleAccess.canViewPage(role, viewPermissions, protectedPageKey);
        boolean canEditCurrentPage = protectedPageKey == null || protectedPageKey.isBlank()
                || RoleAccess.canEditPage(role, editPermissions, protectedPageKey);
        applyPermissionAttributes(request, canViewCurrentPage, canEditCurrentPage, protectedPageKey);

        if (protectedPageKey == null || protectedPageKey.isBlank()) {
            return true;
        }

        if (RoleAccess.PAGE_LICENSE_MANAGEMENT.equals(protectedPageKey) && licenseBypassUser) {
            return true;
        }

        boolean requiresEditPermission = requiresEditPermission(request, protectedPageKey);
        boolean allowed = requiresEditPermission
                ? RoleAccess.canEditPage(role, editPermissions, protectedPageKey)
                : RoleAccess.canViewPage(role, viewPermissions, protectedPageKey);

        if (!allowed) {
            denyAccess(request, response);
            return false;
        }

        return true;
    }

    private String protectedPageKeyForRequest(HttpServletRequest request) {
        String protectedPageKey = resolveProtectedPageKey(request);
        return protectedPageKey == null ? "" : protectedPageKey;
    }

    private void applyPermissionAttributes(HttpServletRequest request,
                                           boolean canViewCurrentPage,
                                           boolean canEditCurrentPage,
                                           String protectedPageKey) {
        request.setAttribute("canViewCurrentPage", canViewCurrentPage);
        request.setAttribute("canEditCurrentPage", canEditCurrentPage);
        request.setAttribute("currentPageKey", protectedPageKey == null ? "" : protectedPageKey);
    }

    private void applyNavigationSessionAttributes(HttpSession session,
                                                  String role,
                                                  Set<String> viewPermissions,
                                                  Set<String> editPermissions) {
        if (session == null) {
            return;
        }

        boolean canViewPmsDataEntry = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_PMS_DATA_ENTRY);
        boolean canViewProductionMetricsData = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_PRODUCTION_METRICS_DATA);
        boolean canViewIssueBoardConfiguration = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_ISSUE_BOARD_CONFIGURATION);
        boolean canViewGembaWalkConfiguration = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION);
        boolean canViewGembaWalkFindings = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_GEMBA_WALK_FINDINGS);
        boolean canViewGembaWalkReporting = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_GEMBA_WALK_REPORTING);
        boolean canViewUserDashboard = RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_USER_DASHBOARD);
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
        boolean canViewMasterDataGroup = canViewUserManagement || canViewEmailConfiguration || canViewKpiPlantName
                || canViewAbnormalityTrackerConfiguration || canViewGembaWalkConfiguration
                || canViewLeadershipGembaTrackerConfiguration || canViewProcessConfirmationConfiguration;
        boolean canEditIssueBoardConfiguration = RoleAccess.canEditPage(role, editPermissions, RoleAccess.PAGE_ISSUE_BOARD_CONFIGURATION);

        session.setAttribute("canViewSettings", RoleAccess.canViewAnyConfigurationPage(role, viewPermissions));
        session.setAttribute("canEditSettings", RoleAccess.canEditAnyConfigurationPage(role, editPermissions));
        session.setAttribute("canViewPmsDataEntry", canViewPmsDataEntry);
        session.setAttribute("canViewProductionMetricsData", canViewProductionMetricsData);
        session.setAttribute("canViewIssueBoardConfiguration", canViewIssueBoardConfiguration);
        session.setAttribute("canEditIssueBoardConfiguration", canEditIssueBoardConfiguration);
        session.setAttribute("canViewGembaWalkConfiguration", canViewGembaWalkConfiguration);
        session.setAttribute("canViewGembaWalkFindings", canViewGembaWalkFindings);
        session.setAttribute("canViewGembaWalkReporting", canViewGembaWalkReporting);
        session.setAttribute("canViewUserDashboard", canViewUserDashboard);
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
        session.setAttribute("canEditUserManagement", RoleAccess.canEditPage(role, editPermissions, RoleAccess.PAGE_USER_MANAGEMENT));
        session.setAttribute("canViewLicenseManagement", canViewLicenseManagement);
        session.setAttribute("canEditLicenseManagement", RoleAccess.canEditPage(role, editPermissions, RoleAccess.PAGE_LICENSE_MANAGEMENT));
        session.setAttribute("canViewEmailConfiguration", canViewEmailConfiguration);
        session.setAttribute("canEditEmailConfiguration", RoleAccess.canEditPage(role, editPermissions, RoleAccess.PAGE_EMAIL_CONFIGURATION));
        session.setAttribute("canViewMasterDataGroup", canViewMasterDataGroup);
        session.setAttribute("canViewConfigurationSettingsGroup",
                canViewIssueBoardConfiguration || canViewGembaWalkConfiguration || canViewLeadershipGembaTrackerConfiguration
                        || canViewGembaWalkFindings || canViewGembaWalkReporting
                        || canViewTrainingScheduleConfiguration || canViewMeetingAgendaConfiguration
                        || canViewProcessConfirmationConfiguration || canViewAbnormalityTrackerConfiguration);
        session.setAttribute("canViewProductionSettingsGroup",
                canViewProductionMetricsData || canViewHsCrossDailyConfiguration || canViewLsrTrackingConfiguration);
        session.setAttribute("canViewKpiConfigurationGroup", canViewKpiTargetCrossColor || canViewKpiRenameDashboard);
        session.setAttribute("canViewKpiDashboard",
                RoleAccess.isAdmin(role) || canViewPmsDataEntry || canViewProductionMetricsData);
    }

    private boolean isReadMethod(String method) {
        return "GET".equalsIgnoreCase(method);
    }

    private boolean requiresEditPermission(HttpServletRequest request, String pageKey) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (RoleAccess.PAGE_LICENSE_MANAGEMENT.equals(pageKey) && path.startsWith("/api/license/decode")) {
            return false;
        }

        if (RoleAccess.PAGE_USER_MANAGEMENT.equals(pageKey) && path.startsWith("/api/users") && "GET".equalsIgnoreCase(method)) {
            return false;
        }

        return !isReadMethod(method);
    }

    private String resolveProtectedPageKey(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (path.startsWith("/pms-configuration") || path.startsWith("/api/users")) {
            return RoleAccess.PAGE_USER_MANAGEMENT;
        }

        if (path.startsWith("/email-configuration") || path.startsWith("/api/email-config")) {
            return RoleAccess.PAGE_EMAIL_CONFIGURATION;
        }

        if (path.startsWith("/api/license")) {
            if (path.equals("/api/license/generate")) {
                return "";
            }
            return RoleAccess.PAGE_LICENSE_MANAGEMENT;
        }

        if (path.startsWith("/api/dashboard-config/plant-name") || path.startsWith("/api/dashboard-config/master-data")) {
            return RoleAccess.PAGE_KPI_PLANT_NAME;
        }

        if (path.startsWith("/api/dashboard-config/abnormality-master-data")) {
            return RoleAccess.PAGE_ABNORMALITY_TRACKER_CONFIGURATION;
        }

        if (path.startsWith("/api/dashboard-config/gemba-walk-master-data")) {
            return RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION;
        }

        if (path.startsWith("/api/dashboard-config/gemba-kaizen-master-data")) {
            return RoleAccess.PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION;
        }

        if (path.startsWith("/api/dashboard-config/process-master-data")) {
            return RoleAccess.PAGE_PROCESS_CONFIRMATION_CONFIGURATION;
        }

        if (path.startsWith("/abnormality-reporting") || path.startsWith("/abnormality-reporting-config")
                || path.startsWith("/api/abnormality-reporting-config")) {
            return RoleAccess.PAGE_ABNORMALITY_TRACKER_CONFIGURATION;
        }

        if (path.startsWith("/settings")) {
            return RoleAccess.pageKeyForSettingsConfig(request.getParameter("config"));
        }

        if (path.startsWith("/pms/") || path.startsWith("/add-daily-data")
                || path.startsWith("/api/priorities") || path.startsWith("/api/daily-performance")
                || path.startsWith("/api/daily-data")) {
            return RoleAccess.PAGE_PMS_DATA_ENTRY;
        }

        if (path.startsWith("/add-metrics") || path.startsWith("/api/metrics")) {
            return RoleAccess.PAGE_PRODUCTION_METRICS_DATA;
        }

        if (path.startsWith("/issue-board") || path.startsWith("/config/issue-board") || path.startsWith("/api/issue-board")) {
            return RoleAccess.PAGE_ISSUE_BOARD_CONFIGURATION;
        }

        if (path.startsWith("/gemba-walk-config") || path.startsWith("/config/gemba-walk")
                || path.startsWith("/api/gemba-walk-config") || path.startsWith("/api/gemba-schedule")) {
            return RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION;
        }

        if (path.startsWith("/gemba-findings")) {
            return RoleAccess.PAGE_GEMBA_WALK_FINDINGS;
        }

        if (path.startsWith("/gemba-reporting")) {
            return RoleAccess.PAGE_GEMBA_WALK_REPORTING;
        }

        if (path.startsWith("/user-dashboard")) {
            return RoleAccess.PAGE_USER_DASHBOARD;
        }

        if (path.startsWith("/config/safety-gemba") || path.startsWith("/api/leadership-gemba-tracker") || path.startsWith("/api/lgt-")) {
            return RoleAccess.PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION;
        }

        if (path.startsWith("/config/training") || path.startsWith("/api/training-schedule")) {
            return RoleAccess.PAGE_TRAINING_SCHEDULE_CONFIGURATION;
        }

        if (path.startsWith("/config/pms-agenda") || path.startsWith("/api/meeting-agenda")) {
            return RoleAccess.PAGE_MEETING_AGENDA_CONFIGURATION;
        }

        if (path.startsWith("/config/process-confirmation") || path.startsWith("/api/process-confirmation")) {
            return RoleAccess.PAGE_PROCESS_CONFIRMATION_CONFIGURATION;
        }

        if (path.startsWith("/config/abnormality") || path.startsWith("/api/abnormality-tracker")) {
            return RoleAccess.PAGE_ABNORMALITY_TRACKER_CONFIGURATION;
        }

        if (path.startsWith("/config/hs-daily") || path.startsWith("/api/hs-daily")) {
            return RoleAccess.PAGE_HS_CROSS_DAILY_CONFIGURATION;
        }

        if (path.startsWith("/config/lsr") || path.startsWith("/api/lsr-daily")) {
            return RoleAccess.PAGE_LSR_TRACKING_CONFIGURATION;
        }

        if (path.startsWith("/api/dashboard-config/info-portal")) {
            return RoleAccess.PAGE_INFO_PORTAL;
        }

        return "";
    }

    private void denyAccess(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String path = request.getRequestURI();
        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Forbidden\"}");
            return;
        }

        response.sendRedirect(request.getContextPath() + "/kpi-dashboard");
    }

    private Set<String> extractPermissions(HttpSession session, String attributeName) {
        if (session == null) {
            return Set.of();
        }

        Object raw = session.getAttribute(attributeName);
        if (raw instanceof Set<?> setValue) {
            Set<String> values = setValue.stream()
                    .filter(v -> v != null)
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.toSet());
            return RoleAccess.sanitizePages(values);
        }

        return Set.of();
    }
}
