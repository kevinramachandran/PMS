package org.example.service;

import org.example.entity.AppUser;
import org.example.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudUserRegressionTest {
    @Test void cloudProfilesPreserveCredentialsAndProtectReservedAccounts() {
        var service = new AuthService();
        var repository = mock(AppUserRepository.class);
        ReflectionTestUtils.setField(service, "appUserRepository", repository);
        ReflectionTestUtils.setField(service, "licenseService", mock(LicenseService.class));
        var old = user(); old.setPassword("existing-hash");
        var incoming = user();
        service.prepareCloudUser(incoming, old);
        assertEquals("existing-hash", incoming.getPassword());
        incoming = user(); incoming.setPassword("new-password");
        service.prepareCloudUser(incoming, old);
        assertTrue(new BCryptPasswordEncoder().matches("new-password", incoming.getPassword()));
        var created = user(); service.prepareCloudUser(created, null);
        assertNotNull(created.getPassword());
        assertFalse(new BCryptPasswordEncoder().matches("", created.getPassword()));
        var reserved = user(); reserved.setUsername("systemadmin");
        assertThrows(IllegalArgumentException.class, () -> service.prepareCloudUser(reserved, null));
        old.setUsername("kevin");
        assertThrows(IllegalArgumentException.class, () -> service.prepareCloudUser(user(), old));
        verify(repository, never()).save(any());
    }
    private AppUser user() {
        var user = new AppUser(); user.setId(77L); user.setUsername("alice"); user.setEmail("alice@example.test"); user.setRole("USER");
        return user;
    }

    @Test void incomingUserMustNotInheritAnotherAccountsPasswordAtTheSameId() {
        var service = new AuthService();
        ReflectionTestUtils.setField(service, "appUserRepository", mock(AppUserRepository.class));
        ReflectionTestUtils.setField(service, "licenseService", mock(LicenseService.class));
        var existing = user(); existing.setUsername("bob"); existing.setPassword("bobs-password-hash");
        var incoming = user();

        service.prepareCloudUser(incoming, existing);

        assertNotEquals(existing.getPassword(), incoming.getPassword());
        assertTrue(incoming.getPassword().startsWith("$2"));
    }
}
