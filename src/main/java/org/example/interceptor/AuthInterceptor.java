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
            return true;
        }

        Set<String> viewPermissions = extractPermissions(session, "viewPermissions");
        Set<String> editPermissions = extractPermissions(session, "editPermissions");
        String method = request.getMethod();

        if (path.startsWith("/pms-configuration")) {
            response.sendRedirect(request.getContextPath() + "/kpi-dashboard");
            return false;
        }

        if (isEmailConfigurationPath(path)) {
            boolean allowed = isReadMethod(method)
                    ? RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_EMAIL_CONFIGURATION)
                    : RoleAccess.canEditPage(role, editPermissions, RoleAccess.PAGE_EMAIL_CONFIGURATION);
            if (!allowed) {
                if (path.startsWith("/api/")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":\"error\",\"message\":\"Forbidden\"}");
                } else {
                    response.sendRedirect(request.getContextPath() + "/kpi-dashboard");
                }
                return false;
            }
        }

        if (isSettingsPath(path)) {
            boolean allowed = isReadMethod(method)
                    ? RoleAccess.canViewPage(role, viewPermissions, RoleAccess.PAGE_SETTINGS)
                    : RoleAccess.canEditPage(role, editPermissions, RoleAccess.PAGE_SETTINGS);
            if (!allowed) {
                if (path.startsWith("/api/")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":\"error\",\"message\":\"Forbidden\"}");
                } else {
                    response.sendRedirect(request.getContextPath() + "/kpi-dashboard");
                }
                return false;
            }
        }

        if (path.startsWith("/api/users")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Forbidden\"}");
            return false;
        }

        if (path.startsWith("/api/license") && !RoleAccess.isAdmin(role) && !internalStaticUser) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Forbidden\"}");
            return false;
        }

        return true;
    }

    private boolean isReadMethod(String method) {
        return "GET".equalsIgnoreCase(method);
    }

    private boolean isEmailConfigurationPath(String path) {
        return path.startsWith("/email-configuration") || path.startsWith("/api/email-config");
    }

    private boolean isSettingsPath(String path) {
        return path.startsWith("/settings")
                || path.startsWith("/add-metrics")
                || path.startsWith("/add-daily-data")
                || path.startsWith("/pms/")
                || path.startsWith("/config/")
                || path.startsWith("/api/priorities")
                || path.startsWith("/api/daily-performance")
                || path.startsWith("/api/daily-data")
                || path.startsWith("/api/metrics")
                || path.startsWith("/api/issue-board")
                || path.startsWith("/api/gemba-schedule")
                || path.startsWith("/api/abnormality-tracker")
                || path.startsWith("/api/lgt-")
                || path.startsWith("/api/training-schedule")
                || path.startsWith("/api/meeting-agenda")
                || path.startsWith("/api/process-confirmation")
                || path.startsWith("/api/dashboard-config");
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
