package org.example.repository;

import org.example.entity.IssueBoardItemHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueBoardItemHistoryRepository extends JpaRepository<IssueBoardItemHistory, Long> {

    List<IssueBoardItemHistory> findByIssueBoardItemIdOrderByEditedAtDescIdDesc(Long issueBoardItemId);
}
