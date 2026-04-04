package org.example.repository;

import org.example.entity.LsrDailyTracking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LsrDailyTrackingRepository extends JpaRepository<LsrDailyTracking, Long> {
    List<LsrDailyTracking> findByYearAndMonth(int year, int month);
    Optional<LsrDailyTracking> findByYearAndMonthAndDay(int year, int month, int day);
}
