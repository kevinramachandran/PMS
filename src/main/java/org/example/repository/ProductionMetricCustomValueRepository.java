package org.example.repository;

import org.example.entity.ProductionMetricCustomValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductionMetricCustomValueRepository extends JpaRepository<ProductionMetricCustomValue, Long> {

    List<ProductionMetricCustomValue> findByProductionMetricsIdIn(Collection<Long> productionMetricsIds);

    List<ProductionMetricCustomValue> findByProductionMetricsId(Long productionMetricsId);

    Optional<ProductionMetricCustomValue> findByProductionMetricsIdAndDefinitionId(Long productionMetricsId, Long definitionId);
}