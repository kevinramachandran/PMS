package org.example.repository;

import org.example.entity.ProcessConfirmationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProcessConfirmationConfigRepository extends JpaRepository<ProcessConfirmationConfig, Long> {

    Optional<ProcessConfirmationConfig> findByPeriodLabel(String periodLabel);

    Optional<ProcessConfirmationConfig> findTopByOrderByConfigDateDescIdDesc();

    void deleteByPeriodLabel(String periodLabel);

    @Query("SELECT DISTINCT p.periodLabel FROM ProcessConfirmationConfig p ORDER BY p.periodLabel DESC")
    List<String> findDistinctPeriodLabelsDesc();
}
