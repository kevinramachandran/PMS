package org.example.config;

import org.example.model.LicenseGeneratePayload;
import org.example.service.LicenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.LocalDate;
import java.util.Optional;

@Component
public class LicenseInitializer {

    @Autowired
    private LicenseService licenseService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeTrialLicense() {
        Optional<?> currentLicense = licenseService.getCurrentLicense();
        if (currentLicense.isPresent()) {
            return; // License already exists, do nothing
        }

        LicenseGeneratePayload payload = new LicenseGeneratePayload();
        payload.setVendorName("Vendor Trail License");
        payload.setDateFrom(LocalDate.now());
        payload.setDateTo(LocalDate.now().plusDays(30));
        payload.setUserCount(100);
        payload.setLicenseText("Trial license activated for 30 days and 100 users.");

        LicenseService.GenerateTokenResult tokenResult = licenseService.generateToken(payload);
        if (!tokenResult.success()) {
            System.err.println("Failed to generate trial license token: " + tokenResult.message());
            return;
        }

        LicenseService.SaveLicenseResult saveResult = licenseService.saveFromToken(tokenResult.token(), "system");
        if (!saveResult.success()) {
            System.err.println("Failed to save trial license: " + saveResult.message());
        } else {
            System.out.println("Trial license generated and saved successfully.");
        }
    }
}
