package org.example.service;

import org.example.controller.AuthController;
import org.example.controller.WebController;
import org.example.interceptor.AuthInterceptor;
import org.example.util.CloudNavigation;
import org.example.util.RoleAccess;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudNavigationTest {
    private MockHttpSession session(String role, String... permissions) {
        var session = new MockHttpSession();
        session.setAttribute("username", "cloud-user");
        session.setAttribute("role", role);
        session.setAttribute("viewPermissions", Set.of(permissions));
        return session;
    }

    @Test void landingUsesPlantThenPermittedConfigurationAndNeverLoopsWithoutAccess() {
        assertEquals("/settings?config=kpi-plant-name", CloudNavigation.landing(session(RoleAccess.ADMIN), true));
        assertEquals("/gemba-walk-config", CloudNavigation.landing(session(RoleAccess.USER, RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION), false));
        assertEquals("/cloud-no-access", CloudNavigation.landing(session(RoleAccess.USER, RoleAccess.PAGE_PMS_DATA_ENTRY), false));
        var editOnly = session(RoleAccess.USER);
        editOnly.setAttribute("editPermissions", Set.of(RoleAccess.PAGE_PROCESS_CONFIRMATION_CONFIGURATION));
        assertEquals("/process-confirmation-config", CloudNavigation.landing(editOnly, false));
        assertEquals("/sync-configuration", CloudNavigation.landing(session(RoleAccess.USER), true));
    }

    @Test void loggedInLoginPageUsesCloudLanding() {
        var controller = new AuthController();
        ReflectionTestUtils.setField(controller, "authService", mock(AuthService.class));
        assertEquals("redirect:/settings?config=kpi-plant-name", controller.loginPage(session(RoleAccess.ADMIN)));
    }

    @Test void oldPagesRedirectButApisAndRetainedPagesStillWork() throws Exception {
        var interceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "authService", mock(AuthService.class));
        var licenses = mock(LicenseService.class);
        when(licenses.evaluateForLogin(anyBoolean())).thenReturn(new LicenseService.LicenseGateResult(true, "OK", "OK"));
        ReflectionTestUtils.setField(interceptor, "licenseService", licenses);
        var handler = new HandlerMethod(new WebController(), WebController.class.getMethod("kpiDashboard"));
        for (String path : new String[]{"/", "/home", "/kpi-dashboard", "/gemba-reporting", "/settings"}) {
            var request = new MockHttpServletRequest("GET", path);
            request.setSession(session(RoleAccess.ADMIN));
            var response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request, response, handler));
            assertEquals("/settings?config=kpi-plant-name", response.getRedirectedUrl());
        }
        for (String path : new String[]{"/api/data-sync/plant-master/export", "/gemba-walk-config"}) {
            var request = new MockHttpServletRequest("GET", path);
            request.setSession(session(RoleAccess.ADMIN));
            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), path.startsWith("/api/") ? new Object() : handler));
        }
        var denied = new MockHttpServletRequest("GET", "/gemba-walk-config");
        denied.setSession(session(RoleAccess.USER));
        var response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(denied, response, handler));
        assertEquals("/cloud-no-access", response.getRedirectedUrl());
        denied.setRequestURI("/cloud-no-access");
        assertTrue(interceptor.preHandle(denied, new MockHttpServletResponse(), handler));
        var records = new MockHttpServletRequest("GET", "/api/gemba-walk-config/records");
        records.setSession(session(RoleAccess.USER, RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION));
        assertTrue(interceptor.preHandle(records, new MockHttpServletResponse(), new Object()));
    }

    @Test void sharedMenuRendersOnlyRetainedPermittedLinks() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        for (var session : new MockHttpSession[]{session(RoleAccess.ADMIN), session(RoleAccess.USER, RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION)}) {
            var pages = CloudNavigation.pages(session, false);
            var context = new Context();
            context.setVariable("cloudConfigPages", pages.stream().filter(p -> !p.master()).toList());
            context.setVariable("cloudMasterPages", pages.stream().filter(CloudNavigation.Page::master).toList());
            String html = engine.process("fragments/cloud-navigation", context);
            assertTrue(html.contains("href=\"/gemba-walk-config\""));
            assertFalse(html.contains("/kpi-dashboard"));
            assertFalse(html.contains("/sync-configuration"));
            if (RoleAccess.USER.equals(session.getAttribute("role"))) assertFalse(html.contains("kpi-plant-name"));
        }
        assertFalse(CloudNavigation.isRetainedPage("/settings", "issue-board"));
        assertTrue(CloudNavigation.isRetainedPage("/settings", "master-process"));
    }
}
