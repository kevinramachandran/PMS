package org.example.repository;

import org.example.entity.ProductionMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductionMetricsRepository extends JpaRepository<ProductionMetrics, Long> {

    Optional<ProductionMetrics> findByDate(LocalDateTime date);

    List<ProductionMetrics> findByDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    boolean existsByDate(LocalDateTime date);

    void deleteByDate(LocalDateTime date);
}
