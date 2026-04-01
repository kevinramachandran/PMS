package org.example.repository;

import org.example.entity.DailyPerformance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyPerformanceRepository extends JpaRepository<DailyPerformance, Long> {

    Optional<DailyPerformance> findByDate(LocalDate date);

    Optional<DailyPerformance> findTopByDateBetweenOrderByDateDesc(LocalDate startDate, LocalDate endDate);
}
