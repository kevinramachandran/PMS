package org.example.service;

import org.example.interceptor.DeleteAccessInterceptor;
import org.example.model.UserInfo;
import org.example.entity.GembaScheduleItem;
import org.example.repository.GembaScheduleItemRepository;
import org.example.util.DeletionGuard;
import org.example.util.RoleAccess;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeleteAccessTest {
    private final AuthService auth = mock(AuthService.class);
    private final DeleteAccessInterceptor interceptor = new DeleteAccessInterceptor(auth);
    private MockHttpServletRequest request(String method, String role) {
        var req = new MockHttpServletRequest(method, "/api/users/1");
        req.getSession().setAttribute("username", "account");
        req.getSession().setAttribute("role", "ADMIN");
        when(auth.findActiveUser("account")).thenReturn(Optional.of(new UserInfo("account", "", "", role, RoleAccess.CONFIG_PAGES, RoleAccess.CONFIG_PAGES)));
        return req;
    }
    @Test void editRightsAndStaleAdminSessionDoNotPermitDelete() throws Exception {
        for (String path : List.of("/api/users/1", "/api/plant-master/1", "/api/production-metrics/all", "/api/gemba-walk/records/1", "/api/abnormality-reporting/records/1", "/api/unknown/1")) {
            var req = request("DELETE", "USER"); req.setRequestURI(path);
            var response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(req, response, new Object()), path);
            assertEquals(403, response.getStatus());
            assertEquals(false, req.getAttribute("canDeleteRecords"));
        }
    }
    @Test void currentAdminAndInternalAccountsPass() throws Exception {
        var admin = request("DELETE", "ADMIN");
        assertTrue(interceptor.preHandle(admin, new MockHttpServletResponse(), new Object()));
        assertEquals(true, admin.getAttribute("canDeleteRecords"));
        var internal = request("DELETE", "USER");
        when(auth.isInternalStaticUser("account")).thenReturn(true);
        assertTrue(interceptor.preHandle(internal, new MockHttpServletResponse(), new Object()));
        assertEquals(true, internal.getAttribute("canDeleteRecords"));
    }
    @Test void absentOrDisabledAccountFailsClosed() throws Exception {
        var req = request("DELETE", "ADMIN");
        when(auth.findActiveUser("account")).thenReturn(Optional.empty());
        assertFalse(interceptor.preHandle(req, new MockHttpServletResponse(), new Object()));
        var response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest("DELETE", "/api/users/1"), response, new Object()));
        assertEquals(401, response.getStatus());
    }
    @Test void normalEditingRemainsAvailableAndPageFlagIsFalse() throws Exception {
        for (String method : List.of("GET", "POST", "PUT", "PATCH")) {
            var req = request(method, "USER");
            assertTrue(interceptor.preHandle(req, new MockHttpServletResponse(), new Object()));
            assertEquals(false, req.getAttribute("canDeleteRecords"));
        }
    }
    @Test void tableReplacementCannotDeleteSavedRowsBeforeWrite() {
        var req = request("POST", "USER"); req.setAttribute("canDeleteRecords", false);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        try {
            var repo = mock(GembaScheduleItemRepository.class);
            var date = java.time.LocalDate.of(2026, 10, 12);
            when(repo.findByScheduleDateOrderByRowOrderAscIdAsc(date)).thenReturn(List.of(new GembaScheduleItem()));
            assertThrows(ResponseStatusException.class, () -> new GembaScheduleItemService(repo).replaceByDate(date, List.of()));
            verify(repo, never()).deleteByScheduleDate(any());
            verify(repo, never()).saveAll(any());
            assertDoesNotThrow(() -> DeletionGuard.checkRemoval(false));
            req.setAttribute("canDeleteRecords", true);
            assertDoesNotThrow(() -> DeletionGuard.checkRemoval(true));
        } finally { RequestContextHolder.resetRequestAttributes(); }
    }
}
