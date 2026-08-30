package org.example.repository;

import org.example.entity.GembaKaizenMasterDataItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GembaKaizenMasterDataItemRepository extends JpaRepository<GembaKaizenMasterDataItem, Long> {

    List<GembaKaizenMasterDataItem> findByCategoryOrderByNameAsc(String category);

    Optional<GembaKaizenMasterDataItem> findByCategoryAndNameIgnoreCase(String category, String name);
}
