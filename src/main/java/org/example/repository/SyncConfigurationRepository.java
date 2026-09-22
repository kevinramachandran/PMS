package org.example.repository;

import org.example.entity.SyncConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncConfigurationRepository extends JpaRepository<SyncConfiguration, Long> {
}