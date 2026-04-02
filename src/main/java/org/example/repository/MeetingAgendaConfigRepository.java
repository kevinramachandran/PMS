package org.example.repository;

import org.example.entity.MeetingAgendaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MeetingAgendaConfigRepository extends JpaRepository<MeetingAgendaConfig, Long> {

    Optional<MeetingAgendaConfig> findByPeriodLabel(String periodLabel);

    Optional<MeetingAgendaConfig> findTopByOrderByConfigDateDescIdDesc();

    void deleteByPeriodLabel(String periodLabel);

    @Query("SELECT DISTINCT m.periodLabel FROM MeetingAgendaConfig m ORDER BY m.periodLabel DESC")
    List<String> findDistinctPeriodLabelsDesc();
}
