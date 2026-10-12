package org.example.service;

import org.example.controller.WebController;

import org.example.util.RoleAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebController.class)
class LocalPagesTest {
    @Autowired MockMvc mvc;
    @MockBean AuthService authService;
    @MockBean LicenseService licenseService;
    @MockBean CloudRoutingService cloudRoutingService;

    @Test void localPagesRenderWithTheSameFullMenu() throws Exception {
        when(licenseService.evaluateForLogin(anyBoolean())).thenReturn(new LicenseService.LicenseGateResult(true, "OK", "OK"));
        when(authService.isSyncConfigurationUser(anyString(), anyString())).thenReturn(true);
        when(authService.findActiveUser("cloud-admin")).thenReturn(java.util.Optional.of(
            new org.example.model.UserInfo("cloud-admin", "", "", RoleAccess.ADMIN, RoleAccess.CONFIG_PAGES, RoleAccess.CONFIG_PAGES)));
        var session = new MockHttpSession();
        session.setAttribute("username", "cloud-admin");
        session.setAttribute("role", RoleAccess.ADMIN);
        for (var page : java.util.List.of("/sync-configuration", "/settings?config=kpi-plant-name", "/pms-configuration", "/gemba-walk-config", "/gemba-kaizen-config", "/abnormality-reporting-config", "/process-confirmation-config", "/email-configuration", "/gemba-reporting", "/gemba-schedule", "/training-schedule", "/meeting-agenda", "/process-confirmation", "/abnormality-tracker", "/leadership-gemba-tracker", "/kpi-dashboard", "/client-selection", "/add-metrics", "/add-daily-data", "/settings?config=metrics-data", "/issue-board", "/gemba-kaizen", "/abnormality-reporting", "/smtp-configuration", "/pms/top-priorities", "/pms/weekly-priorities", "/pms/daily-performance", "/pms/people-daily", "/pms/quality-daily", "/pms/service-daily", "/pms/cost-daily", "/settings?config=master-designation", "/settings?config=master-gemba-walk", "/settings?config=master-gemba-kaizen", "/settings?config=master-process", "/settings?config=master-abnormality", "/settings?config=hs-cross", "/settings?config=lsr-tracking", "/settings?config=kpi-cross-color", "/settings?config=kpi-rename-dashboard", "/settings?config=license", "/user-dashboard")) {
            mvc.perform(get(page).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/css/mobile-responsive.css")))
                .andExpect(content().string(containsString("/js/mobile-responsive.js")))
                .andExpect(content().string(containsString("name=\"viewport\"")))
                .andExpect(content().string(containsString("aria-label=\"Local navigation\"")))
                .andExpect(content().string(containsString("href=\"/kpi-dashboard\"")));
        }
    }

    @Test
    void legacySettingsPermissionIncludesMasterNavigationPages() {
        var pages = RoleAccess.sanitizePages(java.util.Set.of("SETTINGS"));

        org.junit.jupiter.api.Assertions.assertTrue(pages.contains(RoleAccess.PAGE_USER_MANAGEMENT));
        org.junit.jupiter.api.Assertions.assertTrue(pages.contains(RoleAccess.PAGE_EMAIL_CONFIGURATION));
        org.junit.jupiter.api.Assertions.assertTrue(pages.contains(RoleAccess.PAGE_INFO_PORTAL_VIEW));
        org.junit.jupiter.api.Assertions.assertTrue(pages.contains(RoleAccess.PAGE_ABNORMALITY_TRACKER_CONFIGURATION));
        org.junit.jupiter.api.Assertions.assertTrue(pages.contains(RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION));
        org.junit.jupiter.api.Assertions.assertFalse(pages.contains(RoleAccess.PAGE_LICENSE_MANAGEMENT));
    }
}
