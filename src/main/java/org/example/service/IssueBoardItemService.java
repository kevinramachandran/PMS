package org.example.service;

import org.example.entity.IssueBoardItem;
import org.example.entity.IssueBoardItemHistory;
import org.example.model.IssueBoardProgressUpdate;
import org.example.repository.IssueBoardItemHistoryRepository;
import org.example.repository.IssueBoardItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class IssueBoardItemService {

    private static final Logger log = LoggerFactory.getLogger(IssueBoardItemService.class);

    private final IssueBoardItemRepository repository;
    private final IssueBoardItemHistoryRepository historyRepository;
    private final IssueBoardNotificationService notificationService;

    public IssueBoardItemService(IssueBoardItemRepository repository,
                                 IssueBoardItemHistoryRepository historyRepository,
                                 IssueBoardNotificationService notificationService) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.notificationService = notificationService;
    }

    public List<IssueBoardItem> getByBoardDate(LocalDate boardDate) {
        return repository.findByBoardDateOrderByUpdatedAtDescIdDesc(boardDate);
    }

    public List<IssueBoardItem> getLatestBoard() {
        Optional<IssueBoardItem> latest = repository.findTopByOrderByBoardDateDescIdDesc();
        if (latest.isEmpty() || latest.get().getBoardDate() == null) {
            return Collections.emptyList();
        }

        return repository.findByBoardDateOrderByUpdatedAtDescIdDesc(latest.get().getBoardDate());
    }

    public List<IssueBoardItem> searchIssues(String term) {
        String normalized = term == null ? "" : term.trim();
        if (normalized.length() < 2) {
            return Collections.emptyList();
        }

        return repository.searchIssues(normalized, PageRequest.of(0, 12));
    }

    public List<IssueBoardItemHistory> getHistory(Long issueBoardItemId) {
        if (issueBoardItemId == null) {
            return Collections.emptyList();
        }
        return historyRepository.findByIssueBoardItemIdOrderByEditedAtDescIdDesc(issueBoardItemId);
    }

    @Transactional
    public IssueBoardItem updateProgress(Long id, IssueBoardProgressUpdate update, String editedBy) {
        IssueBoardItem item = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found"));

        String user = editedBy == null || editedBy.isBlank() ? "system" : editedBy.trim();
        List<IssueBoardItemHistory> history = new ArrayList<>();

        if (update.getProblem() != null) {
            String problem = cleanText(update.getProblem());
            if (!sameText(item.getProblem(), problem)) {
                history.add(historyEntry(id, "Problem", item.getProblem(), problem, user));
                item.setProblem(problem);
            }
        }
        if (update.getPriority() != null) {
            String priority = cleanText(update.getPriority());
            if (!sameText(item.getPriority(), priority)) {
                history.add(historyEntry(id, "Priority", item.getPriority(), priority, user));
                item.setPriority(priority);
            }
        }
        if (update.getOwnerName() != null) {
            String ownerName = cleanText(update.getOwnerName());
            if (!sameText(item.getOwnerName(), ownerName)) {
                history.add(historyEntry(id, "Name", item.getOwnerName(), ownerName, user));
                item.setOwnerName(ownerName);
            }
        }
        if (update.getIssueDate() != null && !sameText(item.getIssueDate(), update.getIssueDate())) {
            history.add(historyEntry(id, "Date", item.getIssueDate(), update.getIssueDate(), user));
            item.setIssueDate(update.getIssueDate());
        }
        if (update.getRootCause() != null) {
            String rootCause = cleanText(update.getRootCause());
            if (!sameText(item.getRootCause(), rootCause)) {
                history.add(historyEntry(id, "Root Cause", item.getRootCause(), rootCause, user));
                item.setRootCause(rootCause);
            }
        }
        if (update.getActions() != null) {
            String actions = cleanText(update.getActions());
            if (!sameText(item.getActions(), actions)) {
                history.add(historyEntry(id, "Actions", item.getActions(), actions, user));
                item.setActions(actions);
            }
        }
        if (update.getResponsible() != null) {
            String responsible = cleanText(update.getResponsible());
            if (!sameText(item.getResponsible(), responsible)) {
                history.add(historyEntry(id, "Responsible", item.getResponsible(), responsible, user));
                item.setResponsible(responsible);
            }
        }

        boolean targetChanged = false;
        if (!sameDate(item.getTargetDate(), update.getTargetDate())) {
            history.add(historyEntry(id, "Target Date", item.getTargetDate(), update.getTargetDate(), user));
            item.setTargetDate(update.getTargetDate());
            targetChanged = true;
        }
        String targetDateRemark = cleanText(update.getTargetDateRemark());
        if (!sameText(item.getTargetDateRemark(), targetDateRemark)) {
            history.add(historyEntry(id, "Target Date Remark", item.getTargetDateRemark(), targetDateRemark, user));
            item.setTargetDateRemark(targetDateRemark);
        }
        if (!sameDate(item.getTargetDateExtension1(), update.getTargetDateExtension1())) {
            history.add(historyEntry(id, "Target Date Extension 1", item.getTargetDateExtension1(), update.getTargetDateExtension1(), user));
            item.setTargetDateExtension1(update.getTargetDateExtension1());
            targetChanged = true;
        }
        String targetDateExtension1Remark = cleanText(update.getTargetDateExtension1Remark());
        if (!sameText(item.getTargetDateExtension1Remark(), targetDateExtension1Remark)) {
            history.add(historyEntry(id, "Target Date Extension 1 Remark", item.getTargetDateExtension1Remark(), targetDateExtension1Remark, user));
            item.setTargetDateExtension1Remark(targetDateExtension1Remark);
        }
        if (!sameDate(item.getTargetDateExtension2(), update.getTargetDateExtension2())) {
            history.add(historyEntry(id, "Target Date Extension 2", item.getTargetDateExtension2(), update.getTargetDateExtension2(), user));
            item.setTargetDateExtension2(update.getTargetDateExtension2());
            targetChanged = true;
        }
        String targetDateExtension2Remark = cleanText(update.getTargetDateExtension2Remark());
        if (!sameText(item.getTargetDateExtension2Remark(), targetDateExtension2Remark)) {
            history.add(historyEntry(id, "Target Date Extension 2 Remark", item.getTargetDateExtension2Remark(), targetDateExtension2Remark, user));
            item.setTargetDateExtension2Remark(targetDateExtension2Remark);
        }
        if (targetChanged) {
            item.setDueDays(calculateDueDays(effectiveTargetDate(item)));
        }

        String nextStatus = update.getStatus() == null || update.getStatus().isBlank()
                ? "0%"
                : update.getStatus().trim();
        if (!sameText(item.getStatus(), nextStatus)) {
            history.add(historyEntry(id, "Status", item.getStatus(), nextStatus, user));
            item.setStatus(nextStatus);
        }

        if (!sameDate(item.getCompletedDate(), update.getCompletedDate())) {
            history.add(historyEntry(id, "Completed Date", item.getCompletedDate(), update.getCompletedDate(), user));
            item.setCompletedDate(update.getCompletedDate());
        }

        IssueBoardItem saved = repository.save(item);
        if (!history.isEmpty()) {
            historyRepository.saveAll(history);
        }
        return saved;
    }

    @Transactional
    public List<IssueBoardItem> replaceByBoardDate(LocalDate boardDate, List<IssueBoardItem> items) {
        return replaceByBoardDate(boardDate, items, null);
    }

    @Transactional
    public List<IssueBoardItem> replaceByBoardDate(LocalDate boardDate, List<IssueBoardItem> items, String editedBy) {
        List<IssueBoardItem> existingItems = repository.findByBoardDateOrderByRowOrderAscIdAsc(boardDate);
        Map<Long, IssueBoardItem> existingById = new HashMap<>();
        for (IssueBoardItem existing : existingItems) {
            if (existing.getId() != null) {
                existingById.put(existing.getId(), existing);
            }
        }

        int row = 1;
        Set<Long> retainedIds = new HashSet<>();
        List<IssueBoardItem> itemsToSave = new ArrayList<>();
        List<IssueBoardItemHistory> history = new ArrayList<>();
        String user = editedBy == null || editedBy.isBlank() ? "system" : editedBy.trim();
        for (IssueBoardItem item : items) {
            IssueBoardItem itemToSave = null;
            if (item.getId() != null) {
                itemToSave = existingById.get(item.getId());
            }
            if (itemToSave == null) {
                itemToSave = new IssueBoardItem();
            } else {
                appendBulkUpdateHistory(itemToSave, item, user, history);
            }

            copyIssueBoardFields(item, itemToSave);
            itemToSave.setBoardDate(boardDate);
            itemToSave.setRowOrder(item.getRowOrder() == null || item.getRowOrder() < 1 ? row : item.getRowOrder());
            itemToSave.setDueDays(calculateDueDays(effectiveTargetDate(itemToSave)));
            itemsToSave.add(itemToSave);

            if (itemToSave.getId() != null) {
                retainedIds.add(itemToSave.getId());
            }
            row++;
        }

        repository.saveAll(itemsToSave);
        if (!history.isEmpty()) {
            historyRepository.saveAll(history);
        }
        List<IssueBoardItem> removedItems = existingItems.stream()
                .filter(existing -> existing.getId() != null && !retainedIds.contains(existing.getId()))
                .toList();
        if (!removedItems.isEmpty()) {
            repository.deleteAll(removedItems);
        }
        List<IssueBoardItem> savedItems = repository.findByBoardDateOrderByUpdatedAtDescIdDesc(boardDate);

        try {
            notificationService.sendAssignmentNotifications(boardDate, mapByRowOrder(existingItems), mapByRowOrder(savedItems));
        } catch (Exception ex) {
            log.error("Failed to send assignment notifications for boardDate={} — save was still successful", boardDate, ex);
        }
        return savedItems;
    }

    private void copyIssueBoardFields(IssueBoardItem source, IssueBoardItem target) {
        target.setProblem(source.getProblem());
        target.setPriority(source.getPriority());
        target.setOwnerName(source.getOwnerName());
        target.setIssueDate(source.getIssueDate());
        target.setRootCause(source.getRootCause());
        target.setActions(source.getActions());
        target.setResponsible(source.getResponsible());
        target.setTargetDate(source.getTargetDate());
        target.setTargetDateRemark(cleanText(source.getTargetDateRemark()));
        target.setTargetDateExtension1(source.getTargetDateExtension1());
        target.setTargetDateExtension1Remark(cleanText(source.getTargetDateExtension1Remark()));
        target.setTargetDateExtension2(source.getTargetDateExtension2());
        target.setTargetDateExtension2Remark(cleanText(source.getTargetDateExtension2Remark()));
        target.setStatus(source.getStatus());
        target.setCompletedDate(source.getCompletedDate());
        target.setRemarks(source.getRemarks());
        target.setLastReviewDate(source.getLastReviewDate());
        target.setNextReviewDate(source.getNextReviewDate());
    }

    private void appendBulkUpdateHistory(IssueBoardItem existing,
                                         IssueBoardItem incoming,
                                         String user,
                                         List<IssueBoardItemHistory> history) {
        Long id = existing.getId();
        if (id == null) {
            return;
        }

        if (!sameText(existing.getProblem(), incoming.getProblem())) {
            history.add(historyEntry(id, "Problem", existing.getProblem(), incoming.getProblem(), user));
        }
        if (!sameText(existing.getPriority(), incoming.getPriority())) {
            history.add(historyEntry(id, "Priority", existing.getPriority(), incoming.getPriority(), user));
        }
        if (!sameText(existing.getOwnerName(), incoming.getOwnerName())) {
            history.add(historyEntry(id, "Name", existing.getOwnerName(), incoming.getOwnerName(), user));
        }
        if (!sameText(existing.getIssueDate(), incoming.getIssueDate())) {
            history.add(historyEntry(id, "Date", existing.getIssueDate(), incoming.getIssueDate(), user));
        }
        if (!sameText(existing.getRootCause(), incoming.getRootCause())) {
            history.add(historyEntry(id, "Root Cause", existing.getRootCause(), incoming.getRootCause(), user));
        }
        if (!sameText(existing.getActions(), incoming.getActions())) {
            history.add(historyEntry(id, "Actions", existing.getActions(), incoming.getActions(), user));
        }
        if (!sameText(existing.getResponsible(), incoming.getResponsible())) {
            history.add(historyEntry(id, "Responsible", existing.getResponsible(), incoming.getResponsible(), user));
        }
        if (!sameDate(existing.getTargetDate(), incoming.getTargetDate())) {
            history.add(historyEntry(id, "Target Date", existing.getTargetDate(), incoming.getTargetDate(), user));
        }
        if (!sameText(existing.getTargetDateRemark(), incoming.getTargetDateRemark())) {
            history.add(historyEntry(id, "Target Date Remark", existing.getTargetDateRemark(), incoming.getTargetDateRemark(), user));
        }
        if (!sameDate(existing.getTargetDateExtension1(), incoming.getTargetDateExtension1())) {
            history.add(historyEntry(id, "Target Date Extension 1", existing.getTargetDateExtension1(), incoming.getTargetDateExtension1(), user));
        }
        if (!sameText(existing.getTargetDateExtension1Remark(), incoming.getTargetDateExtension1Remark())) {
            history.add(historyEntry(id, "Target Date Extension 1 Remark", existing.getTargetDateExtension1Remark(), incoming.getTargetDateExtension1Remark(), user));
        }
        if (!sameDate(existing.getTargetDateExtension2(), incoming.getTargetDateExtension2())) {
            history.add(historyEntry(id, "Target Date Extension 2", existing.getTargetDateExtension2(), incoming.getTargetDateExtension2(), user));
        }
        if (!sameText(existing.getTargetDateExtension2Remark(), incoming.getTargetDateExtension2Remark())) {
            history.add(historyEntry(id, "Target Date Extension 2 Remark", existing.getTargetDateExtension2Remark(), incoming.getTargetDateExtension2Remark(), user));
        }
        if (!sameText(existing.getStatus(), incoming.getStatus())) {
            history.add(historyEntry(id, "Status", existing.getStatus(), incoming.getStatus(), user));
        }
        if (!sameDate(existing.getCompletedDate(), incoming.getCompletedDate())) {
            history.add(historyEntry(id, "Completed Date", existing.getCompletedDate(), incoming.getCompletedDate(), user));
        }
    }

    private Map<Integer, IssueBoardItem> mapByRowOrder(List<IssueBoardItem> items) {
        Map<Integer, IssueBoardItem> mapped = new LinkedHashMap<>();
        for (IssueBoardItem item : items) {
            if (item.getRowOrder() != null) {
                mapped.put(item.getRowOrder(), item);
            }
        }
        return mapped;
    }

    private IssueBoardItemHistory historyEntry(Long issueBoardItemId,
                                               String fieldName,
                                               Object oldValue,
                                               Object newValue,
                                               String editedBy) {
        IssueBoardItemHistory entry = new IssueBoardItemHistory();
        entry.setIssueBoardItemId(issueBoardItemId);
        entry.setFieldName(fieldName);
        entry.setOldValue(valueText(oldValue));
        entry.setNewValue(valueText(newValue));
        entry.setEditedBy(editedBy);
        entry.setEditedAt(LocalDateTime.now());
        return entry;
    }

    private String valueText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String cleanText(String value) {
        return value == null ? null : value.trim();
    }

    private boolean sameDate(LocalDate first, LocalDate second) {
        return first == null ? second == null : first.equals(second);
    }

    private boolean sameText(String first, String second) {
        String normalizedFirst = first == null ? "" : first.trim();
        String normalizedSecond = second == null ? "" : second.trim();
        return normalizedFirst.equals(normalizedSecond);
    }

    private LocalDate effectiveTargetDate(IssueBoardItem item) {
        if (item.getTargetDateExtension2() != null) {
            return item.getTargetDateExtension2();
        }
        if (item.getTargetDateExtension1() != null) {
            return item.getTargetDateExtension1();
        }
        return item.getTargetDate();
    }

    private Integer calculateDueDays(LocalDate targetDate) {
        if (targetDate == null) {
            return null;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(LocalDate.now(), targetDate));
    }
}
