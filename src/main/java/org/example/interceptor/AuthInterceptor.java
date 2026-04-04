package org.example.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";

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

        if (ROLE_ADMIN.equals(role)) {
            return true;
        }

        // USER role: restricted to KPI dashboard and read-only dashboard APIs.
        if (ROLE_USER.equals(role)) {
            if (path.startsWith("/settings") || path.startsWith("/pms/") || path.startsWith("/config/") || path.startsWith("/pms-configuration")) {
                response.sendRedirect(request.getContextPath() + "/kpi-dashboard");
                return false;
            }

            if (path.startsWith("/api/") && !path.startsWith("/api/auth/")) {
                String method = request.getMethod();
                if (!"GET".equalsIgnoreCase(method)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":\"error\",\"message\":\"Forbidden\"}");
                    return false;
                }
            }

            if (!"/kpi-dashboard".equals(path) && !path.startsWith("/api/") && !"/logout".equals(path)) {
                response.sendRedirect(request.getContextPath() + "/kpi-dashboard");
                return false;
            }
        }

        return true;
    }
}
