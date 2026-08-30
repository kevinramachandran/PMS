package org.example.repository;

import org.example.entity.GembaWalkMasterDataItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GembaWalkMasterDataItemRepository extends JpaRepository<GembaWalkMasterDataItem, Long> {

    List<GembaWalkMasterDataItem> findByCategoryOrderByNameAsc(String category);

    Optional<GembaWalkMasterDataItem> findByCategoryAndNameIgnoreCase(String category, String name);
}
