package org.example.repository;

import org.example.entity.TrainingScheduleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TrainingScheduleRepository extends JpaRepository<TrainingScheduleItem, Long> {

    List<TrainingScheduleItem> findByPeriodLabelOrderByRowOrderAscIdAsc(String periodLabel);

    Optional<TrainingScheduleItem> findTopByOrderByConfigDateDescIdDesc();

    void deleteByPeriodLabel(String periodLabel);

    @Query("SELECT DISTINCT t.periodLabel FROM TrainingScheduleItem t ORDER BY t.periodLabel DESC")
    List<String> findDistinctPeriodLabelsDesc();

    @Query("SELECT DISTINCT t.configDate FROM TrainingScheduleItem t ORDER BY t.configDate DESC")
    List<LocalDate> findDistinctConfigDatesDesc();
}
