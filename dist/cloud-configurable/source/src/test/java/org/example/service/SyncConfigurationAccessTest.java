package org.example.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SyncConfigurationAccessTest {
 @Test void createdAdminCannotConfigureSync() {
  var auth = new AuthService();
  assertFalse(auth.isSyncConfigurationUser("created-admin", "ADMIN"));
  assertTrue(auth.isSyncConfigurationUser("kevin", "ADMIN"));
  assertTrue(auth.isSyncConfigurationUser("siva", "ADMIN"));
  assertFalse(auth.isSyncConfigurationUser(null, "ADMIN"));
 }
}
