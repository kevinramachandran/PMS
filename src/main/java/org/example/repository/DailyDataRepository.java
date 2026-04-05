package org.example.repository;

import org.example.entity.DailyData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DailyDataRepository extends JpaRepository<DailyData, Long> {

    List<DailyData> findByDate(LocalDate date);

    @Query("SELECT d FROM DailyData d WHERE d.type = :type AND d.date = :date ORDER BY d.id DESC")
    List<DailyData> findByTypeAndDate(@Param("type") String type, @Param("date") LocalDate date);

    @Query("SELECT d FROM DailyData d WHERE d.type = :type ORDER BY d.date DESC, d.id DESC")
    List<DailyData> findByType(@Param("type") String type);

    @Query("SELECT MAX(d.date) FROM DailyData d WHERE d.type = :type AND d.date BETWEEN :startDate AND :endDate")
    LocalDate findLatestDateByTypeWithinRange(@Param("type") String type, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    void deleteByTypeAndDate(String type, LocalDate date);

}