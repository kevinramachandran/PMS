package org.example.repository;

import org.example.entity.ProductionMetricSystemDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionMetricSystemDefinitionRepository extends JpaRepository<ProductionMetricSystemDefinition, String> {

    List<ProductionMetricSystemDefinition> findAllByOrderBySectionAscDisplayOrderAscMetricKeyAsc();
}
