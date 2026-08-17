package org.example.repository;

import org.example.entity.IssueBoardItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface IssueBoardItemRepository extends JpaRepository<IssueBoardItem, Long> {

    List<IssueBoardItem> findByBoardDateOrderByRowOrderAscIdAsc(LocalDate boardDate);

    Optional<IssueBoardItem> findTopByOrderByBoardDateDescIdDesc();

    Optional<IssueBoardItem> findTopByOrderByUpdatedAtDescIdDesc();

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

    @Query("SELECT i FROM IssueBoardItem i " +
           "WHERE LOWER(COALESCE(i.problem, '')) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "   OR LOWER(COALESCE(i.actions, '')) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "   OR LOWER(COALESCE(i.rootCause, '')) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "   OR LOWER(COALESCE(i.responsible, '')) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "   OR LOWER(COALESCE(i.ownerName, '')) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "   OR LOWER(COALESCE(i.priority, '')) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "   OR LOWER(COALESCE(i.status, '')) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "ORDER BY i.boardDate DESC, i.rowOrder ASC, i.id ASC")
    List<IssueBoardItem> searchIssues(@Param("term") String term, Pageable pageable);
}
