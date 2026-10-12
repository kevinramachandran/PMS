package org.example.controller;
import org.example.interceptor.AuthInterceptor;
import org.example.service.AuthService;
import org.example.service.LicenseService;
import org.example.model.UserInfo;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class CloudLoginRedirectTest {
 @Test void firstCloudVisitResumesExactMasterPageAfterLogin() throws Exception {
  var auth = mock(AuthService.class);
  var license = mock(LicenseService.class);
  when(auth.authenticate("tester", "password")).thenReturn(Optional.of(new UserInfo("tester", "test@example.com", "password", "ADMIN")));
  when(license.evaluateForLogin(false)).thenReturn(new LicenseService.LicenseGateResult(true, "", ""));
  var interceptor = new AuthInterceptor();
  ReflectionTestUtils.setField(interceptor, "authService", auth);
  ReflectionTestUtils.setField(interceptor, "licenseService", license);
  var controller = new AuthController();
  ReflectionTestUtils.setField(controller, "authService", auth);
  ReflectionTestUtils.setField(controller, "licenseService", license);
  var pages = MockMvcBuilders.standaloneSetup(new WebController()).addInterceptors(interceptor).build();
  var login = MockMvcBuilders.standaloneSetup(controller).build();
  var result = pages.perform(get("/settings?config=master-gemba-walk"))
    .andExpect(status().isFound()).andExpect(redirectedUrl("/pms-login")).andReturn();
  var session = (MockHttpSession) result.getRequest().getSession(false);
  assertNotNull(session);
  assertEquals("/settings?config=master-gemba-walk", session.getAttribute("loginTarget"));
  login.perform(post("/api/auth/login").session(session).contentType("application/json")
    .content("{\"username\":\"tester\",\"password\":\"password\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.redirectUrl").value("/settings?config=master-gemba-walk"));
 }
}
