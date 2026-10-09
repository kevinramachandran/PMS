package org.example.service;

import org.example.entity.AppLicense;
import org.example.model.LicenseGeneratePayload;
import org.example.repository.AppLicenseRepository;
import org.example.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LicenseServiceTest {

    @Test
    void generatedTokenRoundTripsBiosId() {
        LicenseService service = service(false, "SERVER-A", validLicense("SERVER-A"));
        LicenseGeneratePayload payload = validPayload("SERVER-A");

        LicenseService.GenerateTokenResult generated = service.generateToken(payload);
        LicenseService.DecodeTokenResult decoded = service.decodeToken(generated.token());

        assertTrue(generated.success());
        assertTrue(decoded.success());
        assertEquals("SERVER-A", decoded.fields().get("biosId"));
    }

    @Test
    void onPremLicenseRejectsDifferentBiosIdAndAllowsMatchingId() {
        AppLicense license = validLicense("SERVER-B");
        LicenseService service = service(true, "SERVER-A", license);

        LicenseService.LicenseGateResult mismatch = service.evaluateForLogin(false);
        assertFalse(mismatch.allowed());
        assertEquals("LICENSE_HOST_MISMATCH", mismatch.code());
        assertEquals("LICENSE_HOST_MISMATCH", service.toResponse(license).get("status"));

        license.setBiosId(" server-a ");
        LicenseService.LicenseGateResult match = service.evaluateForLogin(false);
        assertTrue(match.allowed());
    }

    @Test
    void cloudModeDoesNotEnforceBiosBinding() {
        LicenseService service = service(false, "SERVER-A", validLicense("SERVER-B"));

        LicenseService.LicenseGateResult result = service.evaluateForLogin(false);

        assertTrue(result.allowed());
    }

    @Test
    void licenseGenerationRequiresBiosIdInEveryDeployment() {
        LicenseService service = service(true, "SERVER-A", validLicense("SERVER-A"));

        LicenseService.GenerateTokenResult generated = service.generateToken(validPayload(""));

        assertFalse(generated.success());
        assertTrue(generated.message().contains("BIOS ID is required"));

        LicenseService cloudService = service(false, "SERVER-A", validLicense("SERVER-A"));
        LicenseService.GenerateTokenResult universal = cloudService.generateToken(validPayload("0000"));
        assertTrue(universal.success());
        assertEquals("0000", cloudService.decodeToken(universal.token()).fields().get("biosId"));
    }

    @Test
    void universalBiosIdWorksOnPremWithoutReadingHostId() {
        LicenseService service = service(true, "SERVER-A", validLicense(" 0000 "));

        LicenseService.LicenseGateResult result = service.evaluateForLogin(false);

        assertTrue(result.allowed());
        verify((HostBiosIdProvider) ReflectionTestUtils.getField(service, "hostBiosIdProvider"), never()).getBiosId();
    }

    @Test
    void automaticOnPremModeRejectsMismatchAndMissingBios() {
        AppLicense license = validLicense("SERVER-B");
        LicenseService service = service(true, "SERVER-A", license);
        CloudRoutingService routing = mock(CloudRoutingService.class);
        when(routing.isCloudManaged()).thenReturn(true);
        ReflectionTestUtils.setField(service, "cloudRoutingService", routing);
        ReflectionTestUtils.setField(service, "biosBindingMode", "auto");
        assertEquals("LICENSE_HOST_MISMATCH", service.evaluateForLogin(false).code());
        license.setBiosId("");
        assertEquals("LICENSE_BIOS_ID_REQUIRED", service.evaluateForLogin(false).code());
        assertTrue(service.evaluateForLogin(true).allowed());
        when(routing.isCloudManaged()).thenReturn(false);
        assertTrue(service.evaluateForLogin(false).allowed());
    }

    @Test
    void loginDoesNotCreateSessionForMismatchedLicense() {
        var auth = mock(AuthService.class);
        var user = new org.example.model.UserInfo("normal-user", "", "", "User", java.util.Set.of(), java.util.Set.of());
        when(auth.authenticate("normal-user", "test")).thenReturn(Optional.of(user));
        var controller = new org.example.controller.AuthController();
        ReflectionTestUtils.setField(controller, "authService", auth);
        ReflectionTestUtils.setField(controller, "licenseService", service(true, "SERVER-A", validLicense("SERVER-B")));
        var session = new org.springframework.mock.web.MockHttpSession();
        var result = controller.login(java.util.Map.of("username", "normal-user", "password", "test"), session);
        assertEquals("LICENSE_HOST_MISMATCH", result.get("code"));
        assertNull(session.getAttribute("username"));
    }

    private LicenseService service(boolean bindingEnabled, String hostBiosId, AppLicense license) {
        LicenseService service = new LicenseService();
        AppLicenseRepository licenseRepository = mock(AppLicenseRepository.class);
        AppUserRepository userRepository = mock(AppUserRepository.class);
        HostBiosIdProvider hostBiosIdProvider = mock(HostBiosIdProvider.class);
        when(licenseRepository.findTopByOrderByIdDesc()).thenReturn(Optional.of(license));
        when(userRepository.count()).thenReturn(1L);
        when(hostBiosIdProvider.getBiosId()).thenReturn(Optional.of(hostBiosId));
        ReflectionTestUtils.setField(service, "appLicenseRepository", licenseRepository);
        ReflectionTestUtils.setField(service, "appUserRepository", userRepository);
        ReflectionTestUtils.setField(service, "hostBiosIdProvider", hostBiosIdProvider);
        ReflectionTestUtils.setField(service, "biosBindingMode", Boolean.toString(bindingEnabled));
        return service;
    }

    private AppLicense validLicense(String biosId) {
        AppLicense license = new AppLicense();
        license.setDateFrom(LocalDate.now().minusDays(1));
        license.setDateTo(LocalDate.now().plusDays(1));
        license.setUserCount(5);
        license.setBiosId(biosId);
        return license;
    }

    private LicenseGeneratePayload validPayload(String biosId) {
        LicenseGeneratePayload payload = new LicenseGeneratePayload();
        payload.setVendorName("Test Vendor");
        payload.setDateFrom(LocalDate.now().minusDays(1));
        payload.setDateTo(LocalDate.now().plusDays(30));
        payload.setUserCount(5);
        payload.setLicenseText("Test license");
        payload.setBiosId(biosId);
        return payload;
    }
}