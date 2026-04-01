package org.example.repository;

import org.example.entity.ProductionMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductionMetricsRepository extends JpaRepository<ProductionMetrics, Long> {

    Optional<ProductionMetrics> findByDate(LocalDateTime date);

    List<ProductionMetrics> findByDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<ProductionMetrics> findByDateBetweenOrderByDateAsc(LocalDateTime startDate, LocalDateTime endDate);

    boolean existsByDate(LocalDateTime date);

    void deleteByDate(LocalDateTime date);

    default Optional<ProductionMetrics> findByDate(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(23, 59, 59);
        List<ProductionMetrics> dayRecords = findByDateBetweenOrderByDateAsc(start, end);
        if (dayRecords.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(dayRecords.get(0));
    }
}
