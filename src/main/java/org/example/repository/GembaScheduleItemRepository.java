package org.example.repository;

import org.example.entity.GembaScheduleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GembaScheduleItemRepository extends JpaRepository<GembaScheduleItem, Long> {

    List<GembaScheduleItem> findByScheduleDateOrderByRowOrderAscIdAsc(LocalDate scheduleDate);

    Optional<GembaScheduleItem> findTopByOrderByScheduleDateDescIdDesc();

    @Query("SELECT DISTINCT g.scheduleDate FROM GembaScheduleItem g ORDER BY g.scheduleDate DESC")
    List<LocalDate> findDistinctScheduleDatesDesc();

    void deleteByScheduleDate(LocalDate scheduleDate);
}
