package org.example.repository;

import org.example.entity.AppLicense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppLicenseRepository extends JpaRepository<AppLicense, Long> {

    Optional<AppLicense> findTopByOrderByIdDesc();

    Optional<AppLicense> findByLicenseToken(String licenseToken);
}
