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
  var auth = mock(AuthService.class);
  when(auth.findActiveUser("created-admin")).thenReturn(java.util.Optional.of(
      new org.example.model.UserInfo("created-admin", "", "", role, java.util.Set.of(), java.util.Set.of())));
  ReflectionTestUtils.setField(interceptor, "authService", auth);
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
 @Test void adminPeerCanTransferDataAndAttachmentsButOrdinaryUsersCannot() throws Exception {
  for (String path : new String[]{"/api/cloud-sync/identity", "/api/cloud-sync/data/users", "/api/cloud-sync/attachments/gemba-walk/image.png"}) {
   assertTrue(allowed("ADMIN", "GET", path));
   assertFalse(allowed("USER", "GET", path));
  }
  assertTrue(allowed("ADMIN", "POST", "/api/cloud-sync/push"));
  assertTrue(allowed("ADMIN", "PUT", "/api/cloud-sync/attachments/gemba-walk/image.png"));
  assertFalse(allowed("USER", "POST", "/api/cloud-sync/push"));
  assertFalse(allowed("USER", "PUT", "/api/cloud-sync/attachments/gemba-walk/image.png"));
 }
}
