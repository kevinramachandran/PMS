package org.example.service;

import org.example.interceptor.AuthInterceptor;
import org.example.model.UserInfo;
import org.example.model.MetricsEntryBundlePayload;
import org.example.controller.MetricsEntryController;
import org.example.util.RoleAccess;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionRegressionTest {
    record Page(String path, String key) {}
    private final AuthService auth = mock(AuthService.class);
    private final LicenseService license = mock(LicenseService.class);
    private final AuthInterceptor interceptor = new AuthInterceptor();
    PermissionRegressionTest() {
        ReflectionTestUtils.setField(interceptor, "authService", auth);
        ReflectionTestUtils.setField(interceptor, "licenseService", license);
        ReflectionTestUtils.setField(interceptor, "cloudRoutingService", mock(CloudRoutingService.class));
        when(license.evaluateForLogin(anyBoolean())).thenReturn(LicenseService.LicenseGateResult.allow("OK"));
    }
    private MockHttpServletRequest request(String path, String method, Set<String> views, Set<String> edits, String role) {
        when(auth.findActiveUser("test-user")).thenReturn(Optional.of(new UserInfo("test-user", "", "", role, views, edits)));
        var request = new MockHttpServletRequest(method, path);
        request.getSession().setAttribute("username", "test-user");
        request.getSession().setAttribute("role", role);
        if (path.startsWith("/settings?")) {
            request.setRequestURI("/settings"); request.setParameter("config", path.substring(path.indexOf('=') + 1));
        }
        return request;
    }
    @Test void onPremAdminCannotWriteUsersLocally() throws Exception {
        var routing = mock(CloudRoutingService.class);
        when(routing.blocksWrite("/api/users/123", "PUT")).thenReturn(true);
        ReflectionTestUtils.setField(interceptor, "cloudRoutingService", routing);
        var req = request("/api/users/123", "PUT", Set.of(), Set.of(), RoleAccess.ADMIN);
        var response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req, response, new Object()));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("CLOUD_MANAGED_EDITS"));
    }

    private List<Page> pages() {
        return List.of(
            new Page("/pms/daily-performance", "PMS_DATA_ENTRY"),
            new Page("/settings?config=metrics-data", "PRODUCTION_METRICS_DATA"),
            new Page("/issue-board", "ISSUE_BOARD_CONFIGURATION"),
            new Page("/gemba-schedule", "GEMBA_WALK_CONFIGURATION"),
            new Page("/gemba-walk-config", "GEMBA_WALK_CONFIGURATION"),
            new Page("/gemba-reporting", "GEMBA_WALK_REPORTING"),
            new Page("/gemba-kaizen-config", "LEADERSHIP_GEMBA_TRACKER_CONFIGURATION"),
            new Page("/leadership-gemba-tracker", "LEADERSHIP_GEMBA_TRACKER_CONFIGURATION"),
            new Page("/training-schedule", "TRAINING_SCHEDULE_CONFIGURATION"),
            new Page("/meeting-agenda", "MEETING_AGENDA_CONFIGURATION"),
            new Page("/process-confirmation", "PROCESS_CONFIRMATION_CONFIGURATION"),
            new Page("/process-confirmation-config", "PROCESS_CONFIRMATION_CONFIGURATION"),
            new Page("/abnormality-reporting-config", "ABNORMALITY_TRACKER_CONFIGURATION"),
            new Page("/abnormality-tracker", "ABNORMALITY_TRACKER_CONFIGURATION"),
            new Page("/settings?config=hs-cross", "HS_CROSS_DAILY_CONFIGURATION"),
            new Page("/settings?config=lsr-tracking", "LSR_TRACKING_CONFIGURATION"),
            new Page("/settings?config=info-portal", "INFO_PORTAL"),
            new Page("/settings?config=kpi-rename-dashboard", "KPI_RENAME_DASHBOARD"),
            new Page("/settings?config=kpi-plant-name", "KPI_PLANT_NAME"),
            new Page("/pms-configuration", "USER_MANAGEMENT"),
            new Page("/settings?config=license", "LICENSE_MANAGEMENT"),
            new Page("/email-configuration", "EMAIL_CONFIGURATION"));
    }
    @Test void everyPageDeniesMissingRightsAndExposesTheCorrectViewOnlyFlag() throws Exception {
        for (var page : pages()) {
            var absent = request(page.path(), "GET", Set.of(), Set.of(), "USER");
            assertFalse(interceptor.preHandle(absent, new MockHttpServletResponse(), new Object()), page.path());
            var viewer = request(page.path(), "GET", Set.of(page.key()), Set.of(), "USER");
            assertTrue(interceptor.preHandle(viewer, new MockHttpServletResponse(), new Object()), page.path());
            assertEquals(false, viewer.getAttribute("canEditCurrentPage"), page.path());
            var editor = request(page.path(), "GET", Set.of(page.key()), Set.of(page.key()), "USER");
            assertTrue(interceptor.preHandle(editor, new MockHttpServletResponse(), new Object()), page.path());
            assertEquals(true, editor.getAttribute("canEditCurrentPage"), page.path());
            var admin = request(page.path(), "GET", Set.of(), Set.of(), "ADMIN");
            assertTrue(interceptor.preHandle(admin, new MockHttpServletResponse(), new Object()), page.path());
            assertEquals(true, admin.getAttribute("canEditCurrentPage"), page.path());
        }
    }
    @Test void mutationApisRejectViewOnlyUsers() throws Exception {
        for (var page : List.of(new Page("/api/issue-board", "ISSUE_BOARD_CONFIGURATION"),
                new Page("/api/abnormality-reporting-config/records", "ABNORMALITY_TRACKER_CONFIGURATION"),
                new Page("/api/gemba-walk-config/records", "GEMBA_WALK_CONFIGURATION"),
                new Page("/api/gemba-kaizen-config/records", "LEADERSHIP_GEMBA_TRACKER_CONFIGURATION"),
                new Page("/api/carlex-process-confirmation/records", "PROCESS_CONFIRMATION_CONFIGURATION"),
                new Page("/api/daily-performance", "PMS_DATA_ENTRY"),
                new Page("/api/production-metrics/import/csv", "PRODUCTION_METRICS_DATA"))) {
            for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
                var response = new MockHttpServletResponse();
                assertFalse(interceptor.preHandle(request(page.path(), method, Set.of(page.key()), Set.of(), "USER"), response, new Object()));
                assertEquals(403, response.getStatus());
                assertTrue(interceptor.preHandle(request(page.path(), method, Set.of(page.key()), Set.of(page.key()), "USER"), new MockHttpServletResponse(), new Object()));
            }
        }
    }
    @Test void revokedAdminSessionUsesFreshPermissionsAndInactiveUserIsRejected() throws Exception {
        var request = request("/api/users", "POST", Set.of("USER_MANAGEMENT"), Set.of(), "USER");
        request.getSession().setAttribute("role", "ADMIN");
        assertFalse(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals("USER", request.getSession().getAttribute("role"));
        when(auth.findActiveUser("test-user")).thenReturn(Optional.empty());
        var response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }
    @Test void sectionGrantDoesNotAllowOtherSectionsOrBulkWrites() throws Exception {
        var service = mock(ProductionMetricsService.class);
        var controller = new MetricsEntryController(service);
        var request = request("/api/metrics", "POST", Set.of("PRODUCTION_METRICS_DATA_PEOPLE"), Set.of("PRODUCTION_METRICS_DATA_PEOPLE"), "USER");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(true, request.getSession().getAttribute("canEditMetricsPEOPLE"));
        assertEquals(false, request.getSession().getAttribute("canEditMetricsCOST"));
        var payload = new MetricsEntryBundlePayload();
        payload.getActual().getCost().put("kpiEnergyKwhHlFtdActual", 2);
        assertEquals(403, controller.saveMetricsEntry(payload, request.getSession()).getStatusCode().value());
        verifyNoInteractions(service);
        payload.getActual().getCost().clear();
        payload.getActual().getPeople().put("productionProductivityFtdActual", 1);
        assertEquals(200, controller.saveMetricsEntry(payload, request.getSession()).getStatusCode().value());
        request.setRequestURI("/api/production-metrics/import/csv");
        assertFalse(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
    @Test void configurationReadersCanLoadGembaRowsButPortalViewDoesNotGrantConfiguration() throws Exception {
        assertTrue(interceptor.preHandle(request("/api/gemba-walk-config/records", "GET", Set.of("GEMBA_WALK_CONFIGURATION"), Set.of(), "USER"), new MockHttpServletResponse(), new Object()));
        assertFalse(interceptor.preHandle(request("/settings?config=info-portal", "GET", Set.of("INFO_PORTAL_VIEW"), Set.of(), "USER"), new MockHttpServletResponse(), new Object()));
        assertFalse(interceptor.preHandle(request("/kpi-dashboard", "GET", Set.of(), Set.of(), "USER"), new MockHttpServletResponse(), new Object()));
    }
}
