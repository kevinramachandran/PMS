package org.example.interceptor;
import org.example.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdminSyncAccessTest {
 private boolean allowed(String role, String method, String path) throws Exception {
  var interceptor = new AuthInterceptor();
  ReflectionTestUtils.setField(interceptor, "authService", mock(AuthService.class));
  var request = new MockHttpServletRequest(method, path);
  request.getSession().setAttribute("username", "created-admin");
  request.getSession().setAttribute("role", role);
  return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
 }
 @Test void adminCanRunAndReadStatus() throws Exception {
  assertTrue(allowed("ADMIN", "POST", "/api/cloud-sync/run"));
  assertTrue(allowed("ADMIN", "GET", "/api/cloud-sync/status"));
 }
 @Test void adminCannotConfigureSync() throws Exception {
  assertFalse(allowed("ADMIN", "GET", "/sync-configuration"));
  assertFalse(allowed("ADMIN", "GET", "/api/cloud-sync/config"));
  assertFalse(allowed("ADMIN", "PUT", "/api/cloud-sync/config"));
 }
 @Test void ordinaryUserCannotSync() throws Exception {
  assertFalse(allowed("USER", "POST", "/api/cloud-sync/run"));
 }
}
