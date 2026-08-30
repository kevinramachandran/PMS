package org.example.repository;

import org.example.entity.AbnormalityMasterDataItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AbnormalityMasterDataItemRepository extends JpaRepository<AbnormalityMasterDataItem, Long> {

    List<AbnormalityMasterDataItem> findByCategoryOrderByNameAsc(String category);

    Optional<AbnormalityMasterDataItem> findByCategoryAndNameIgnoreCase(String category, String name);
}
