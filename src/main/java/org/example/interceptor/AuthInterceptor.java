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

        boolean internalStaticUser = authService.isInternalStaticUser(username);

        // /api/license/generate is open — skip license gate
        if (!path.equals("/api/license/generate")) {
            LicenseService.LicenseGateResult gate = licenseService.evaluateForLogin(internalStaticUser);
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
            applyPermissionAttributes(request, true, true, protectedPageKeyForRequest(request));
            return true;
        }

        Set<String> viewPermissions = extractPermissions(session, "viewPermissions");
        Set<String> editPermissions = extractPermissions(session, "editPermissions");
        String protectedPageKey = resolveProtectedPageKey(request);
        boolean canViewCurrentPage = protectedPageKey == null || protectedPageKey.isBlank()
                || RoleAccess.canViewPage(role, viewPermissions, protectedPageKey);
        boolean canEditCurrentPage = protectedPageKey == null || protectedPageKey.isBlank()
                || RoleAccess.canEditPage(role, editPermissions, protectedPageKey);
        applyPermissionAttributes(request, canViewCurrentPage, canEditCurrentPage, protectedPageKey);

        if (protectedPageKey == null || protectedPageKey.isBlank()) {
            return true;
        }

        if (RoleAccess.PAGE_LICENSE_MANAGEMENT.equals(protectedPageKey) && internalStaticUser) {
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

        if (path.startsWith("/config/issue-board") || path.startsWith("/api/issue-board")) {
            return RoleAccess.PAGE_ISSUE_BOARD_CONFIGURATION;
        }

        if (path.startsWith("/config/gemba-walk") || path.startsWith("/api/gemba-schedule")) {
            return RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION;
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

        if (path.startsWith("/api/dashboard-config/kpi-footer-buttons")) {
            return RoleAccess.PAGE_KPI_FOOTER_BUTTONS;
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
