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

    @Test void localPagesRenderWithTheSameFullMenu() throws Exception {
        when(licenseService.evaluateForLogin(anyBoolean())).thenReturn(new LicenseService.LicenseGateResult(true, "OK", "OK"));
        when(authService.isSyncConfigurationUser(anyString(), anyString())).thenReturn(true);
        var session = new MockHttpSession();
        session.setAttribute("username", "cloud-admin");
        session.setAttribute("role", RoleAccess.ADMIN);
        for (var page : java.util.List.of("/sync-configuration", "/settings?config=kpi-plant-name", "/pms-configuration", "/gemba-walk-config", "/gemba-kaizen-config", "/abnormality-reporting-config", "/process-confirmation-config", "/email-configuration")) {
            mvc.perform(get(page).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-label=\"Local navigation\"")))
                .andExpect(content().string(containsString("href=\"/kpi-dashboard\"")));
        }
    }
}
