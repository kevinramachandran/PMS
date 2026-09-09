package org.example.repository;

import org.example.entity.AssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentHistoryRepository extends JpaRepository<AssignmentHistory, Long> {
    List<AssignmentHistory> findByModuleAndRecordIdOrderByAssignedAtDescIdDesc(String module, Long recordId);
    long countByModuleAndRecordId(String module, Long recordId);
}
