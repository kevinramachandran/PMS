package org.example.repository;

import org.example.entity.IssueBoardItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface IssueBoardItemRepository extends JpaRepository<IssueBoardItem, Long> {

    List<IssueBoardItem> findByBoardDateOrderByRowOrderAscIdAsc(LocalDate boardDate);

    Optional<IssueBoardItem> findTopByOrderByBoardDateDescIdDesc();

    void deleteByBoardDate(LocalDate boardDate);
}
