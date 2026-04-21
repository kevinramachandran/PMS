package org.example.repository;

import org.example.entity.ProductionMetricCustomDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionMetricCustomDefinitionRepository extends JpaRepository<ProductionMetricCustomDefinition, Long> {

    List<ProductionMetricCustomDefinition> findByActiveTrueOrderBySectionAscDisplayOrderAscIdAsc();

    boolean existsByMetricKeyIgnoreCase(String metricKey);

    long countBySectionAndActiveTrue(String section);
}