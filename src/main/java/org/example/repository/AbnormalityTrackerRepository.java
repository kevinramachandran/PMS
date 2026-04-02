package org.example.repository;

import org.example.entity.AbnormalityTrackerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AbnormalityTrackerRepository extends JpaRepository<AbnormalityTrackerEntry, Long> {

    List<AbnormalityTrackerEntry> findByPeriodLabelOrderByRowOrderAscIdAsc(String periodLabel);

    Optional<AbnormalityTrackerEntry> findTopByOrderByRecordDateDescIdDesc();

    @Query("SELECT DISTINCT a.periodLabel FROM AbnormalityTrackerEntry a ORDER BY a.periodLabel DESC")
    List<String> findDistinctPeriodLabelsDesc();

    void deleteByPeriodLabel(String periodLabel);
}
