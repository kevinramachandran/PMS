package org.example.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.util.RoleAccess;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler) throws Exception {

        HttpSession session  = request.getSession(false);
        String username      = (session != null) ? (String) session.getAttribute("username") : null;
        String role          = (session != null) ? (String) session.getAttribute("role")     : null;

        String path = request.getRequestURI();

        // Not logged in: APIs must return 401 JSON (not HTML redirect), pages redirect to login.
        if (username == null) {
            if (path.startsWith("/api/") && !path.startsWith("/api/auth/")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":\"error\",\"message\":\"Unauthorized\"}");
                return false;
            }

            response.sendRedirect(request.getContextPath() + "/pms-login");
            return false;
        }

        if (RoleAccess.isAdmin(role)) {
            return true;
        }

        if (path.startsWith("/pms-configuration")) {
            response.sendRedirect(request.getContextPath() + "/kpi-dashboard");
            return false;
        }

        if (path.startsWith("/email-configuration") || path.startsWith("/api/email-config")) {
            if (!RoleAccess.canAccessEmailConfiguration(role)) {
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

        if (path.startsWith("/settings") || path.startsWith("/pms/") || path.startsWith("/config/")
                || path.startsWith("/add-metrics") || path.startsWith("/add-daily-data")) {
            if (!RoleAccess.canAccessPmsDataEntry(role)) {
                response.sendRedirect(request.getContextPath() + "/kpi-dashboard");
                return false;
            }
        }

        if (path.startsWith("/api/users")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Forbidden\"}");
            return false;
        }

        if (path.startsWith("/api/") && !path.startsWith("/api/auth/")) {
            String method = request.getMethod();
            if (!RoleAccess.canAccessPmsDataEntry(role) && !"GET".equalsIgnoreCase(method)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":\"error\",\"message\":\"Forbidden\"}");
                return false;
            }
        }

        return true;
    }
}
