package org.example.repository;

import org.example.entity.ProcessMasterDataItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProcessMasterDataItemRepository extends JpaRepository<ProcessMasterDataItem, Long> {

    List<ProcessMasterDataItem> findByCategoryOrderByNameAsc(String category);

    Optional<ProcessMasterDataItem> findByCategoryAndNameIgnoreCase(String category, String name);
}
