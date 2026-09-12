package org.example.repository;

import org.example.entity.PlantMasterDataItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlantMasterDataItemRepository extends JpaRepository<PlantMasterDataItem, Long> {

    List<PlantMasterDataItem> findByCategoryOrderByParentPlantAscParentDepartmentAscParentProcessAreaAscNameAsc(String category);

    Optional<PlantMasterDataItem> findFirstByCategoryAndNameIgnoreCase(String category, String name);
}
