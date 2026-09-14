package org.example.service;

import org.example.entity.AssignmentHistory;
import org.example.repository.AssignmentHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AssignmentHistoryService {
    private final AssignmentHistoryRepository historyRepository;

    public AssignmentHistoryService(AssignmentHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public List<AssignmentHistory> history(String module, Long recordId) {
        return historyRepository.findByModuleAndRecordIdOrderByAssignedAtDescIdDesc(module, recordId);
    }

    /** Validates the two optional reassignment slots used by the workflow forms. */
    public void validateSlots(String reassignedTo1, String reassignment1Remark,
                              String reassignedTo2, String reassignment2Remark) {
        boolean first = !clean(reassignedTo1).isBlank();
        boolean second = !clean(reassignedTo2).isBlank();
        if (first && clean(reassignment1Remark).isBlank()) {
            throw new IllegalArgumentException("Remarks are required for Reassign 1");
        }
        if (second && !first) {
            throw new IllegalArgumentException("Reassign 1 must be selected before Reassign 2");
        }
        if (second && clean(reassignment2Remark).isBlank()) {
            throw new IllegalArgumentException("Remarks are required for Reassign 2");
        }
    }

    public void validateTransition(boolean creating, String oldInitial, String initial,
                                   String oldFirst, String first, String firstRemark,
                                   String oldSecond, String second, String secondRemark) {
        if (creating) {
            if (!clean(first).isBlank() || !clean(second).isBlank()
                    || !clean(firstRemark).isBlank() || !clean(secondRemark).isBlank()) {
                throw new IllegalArgumentException("Save the initial assignment before reassigning");
            }
            return;
        }
        if (!clean(oldInitial).equalsIgnoreCase(clean(initial))) {
            throw new IllegalArgumentException("Use the next reassignment field to change the assignee");
        }
        if ((!clean(oldFirst).isBlank() && !clean(oldFirst).equalsIgnoreCase(clean(first)))
                || (!clean(oldSecond).isBlank() && !clean(oldSecond).equalsIgnoreCase(clean(second)))) {
            throw new IllegalArgumentException("Saved reassignment stages cannot be replaced");
        }
        if (clean(oldFirst).isBlank() && !clean(second).isBlank()) {
            throw new IllegalArgumentException("Save Reassign 1 before selecting Reassign 2");
        }
        validateSlots(first, firstRemark, second, secondRemark);
    }

    public void recordStages(String module, Long id, String initial, String oldFirst, String first,
                             String firstRemark, String oldSecond, String second, String secondRemark,
                             String actor, String scope) {
        if (!clean(oldFirst).equalsIgnoreCase(clean(first))) {
            record(module, id, initial, first, firstRemark, actor, scope);
        }
        if (!clean(oldSecond).equalsIgnoreCase(clean(second))) {
            record(module, id, first, second, secondRemark, actor, scope);
        }
    }

    @Transactional
    public void record(String module, Long recordId, String oldAssignee, String newAssignee,
                       String remarks, String actorUsername, String scope) {
        String from = clean(oldAssignee);
        String to = clean(newAssignee);
        if (from.equalsIgnoreCase(to)) return;
        if (to.isBlank()) return;
        if (!from.isBlank() && (remarks == null || remarks.trim().isBlank())) {
            throw new IllegalArgumentException("Remarks are required for reassignment");
        }
        AssignmentHistory entry = new AssignmentHistory();
        entry.setModule(module); entry.setRecordId(recordId); entry.setFromAssignee(from);
        entry.setToAssignee(to); entry.setAssignedBy(clean(actorUsername)); entry.setRemarks(clean(remarks));
        entry.setEscalationLevel(from.isBlank() ? 0 : 1); entry.setAssignedAt(LocalDateTime.now());
        historyRepository.save(entry);
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
}
