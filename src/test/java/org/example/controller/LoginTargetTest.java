package org.example.controller;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
class LoginTargetTest {
 @Test void resumesRequestedPageWithQueryAndConsumesIt() {
  var session = new MockHttpSession();
  session.setAttribute("loginTarget", "/settings?config=master-gemba-walk");
  String target = ReflectionTestUtils.invokeMethod(new AuthController(), "consumeLoginTarget", session);
  assertEquals("/settings?config=master-gemba-walk", target);
  assertNull(session.getAttribute("loginTarget"));
 }
}
