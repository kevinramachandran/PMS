package org.example.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.service.AuthService;
import org.example.util.RoleAccess;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class DeleteAccessInterceptor implements HandlerInterceptor {
    private final AuthService authService;

    public DeleteAccessInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        var session = request.getSession(false);
        String username = session == null ? null : (String) session.getAttribute("username");
        boolean allowed = username != null && (authService.isInternalStaticUser(username)
                || authService.findActiveUser(username).map(user -> RoleAccess.isAdmin(user.getRole())).orElse(false));
        request.setAttribute("canDeleteRecords", allowed);
        if ("DELETE".equalsIgnoreCase(request.getMethod()) && !allowed) {
            response.setStatus(username == null ? 401 : 403);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Access denied\"}");
            return false;
        }
        return true;
    }
}
