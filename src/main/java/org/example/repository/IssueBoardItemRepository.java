package org.example.repository;

import org.example.entity.IssueBoardItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface IssueBoardItemRepository extends JpaRepository<IssueBoardItem, Long> {

    List<IssueBoardItem> findByBoardDateOrderByRowOrderAscIdAsc(LocalDate boardDate);

    Optional<IssueBoardItem> findTopByOrderByBoardDateDescIdDesc();

    void deleteByBoardDate(LocalDate boardDate);

    /**
     * Returns every open issue that has a target date set.
     * An issue is considered open when it has no completedDate and its
     * status is neither "100%" nor "closed".
     */
    @Query("SELECT i FROM IssueBoardItem i " +
           "WHERE i.targetDate IS NOT NULL " +
           "  AND i.completedDate IS NULL " +
           "  AND LOWER(i.status) NOT IN ('100%', 'closed') " +
           "ORDER BY i.boardDate DESC, i.rowOrder ASC, i.id ASC")
    List<IssueBoardItem> findAllOpenItemsWithTargetDate();
}
