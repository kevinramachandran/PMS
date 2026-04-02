package org.example.repository;

import org.example.entity.LeadershipGembaTrackerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeadershipGembaTrackerRepository extends JpaRepository<LeadershipGembaTrackerEntry, Long> {

    List<LeadershipGembaTrackerEntry> findByPeriodLabelOrderByRowOrderAscIdAsc(String periodLabel);

    Optional<LeadershipGembaTrackerEntry> findTopByOrderByScheduleDateDescIdDesc();

    @Query("SELECT DISTINCT l.periodLabel FROM LeadershipGembaTrackerEntry l ORDER BY l.periodLabel DESC")
    List<String> findDistinctPeriodLabelsDesc();

    void deleteByPeriodLabel(String periodLabel);

    @Query("SELECT DISTINCT l.scheduleDate FROM LeadershipGembaTrackerEntry l ORDER BY l.scheduleDate DESC")
    List<LocalDate> findDistinctDatesDesc();
}
