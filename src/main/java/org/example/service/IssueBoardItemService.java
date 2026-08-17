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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        return repository.findByBoardDateOrderByRowOrderAscIdAsc(boardDate);
    }

    public List<IssueBoardItem> getLatestBoard() {
        Optional<IssueBoardItem> latest = repository.findTopByOrderByBoardDateDescIdDesc();
        if (latest.isEmpty() || latest.get().getBoardDate() == null) {
            return Collections.emptyList();
        }

        return repository.findByBoardDateOrderByRowOrderAscIdAsc(latest.get().getBoardDate());
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

        boolean targetChanged = false;
        if (!sameDate(item.getTargetDate(), update.getTargetDate())) {
            history.add(historyEntry(id, "Target Date", item.getTargetDate(), update.getTargetDate(), user));
            item.setTargetDate(update.getTargetDate());
            targetChanged = true;
        }
        if (!sameDate(item.getTargetDateExtension1(), update.getTargetDateExtension1())) {
            history.add(historyEntry(id, "Target Date Extension 1", item.getTargetDateExtension1(), update.getTargetDateExtension1(), user));
            item.setTargetDateExtension1(update.getTargetDateExtension1());
            targetChanged = true;
        }
        if (!sameDate(item.getTargetDateExtension2(), update.getTargetDateExtension2())) {
            history.add(historyEntry(id, "Target Date Extension 2", item.getTargetDateExtension2(), update.getTargetDateExtension2(), user));
            item.setTargetDateExtension2(update.getTargetDateExtension2());
            targetChanged = true;
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
        List<IssueBoardItem> existingItems = repository.findByBoardDateOrderByRowOrderAscIdAsc(boardDate);
        repository.deleteByBoardDate(boardDate);

        int row = 1;
        for (IssueBoardItem item : items) {
            item.setId(null);
            item.setBoardDate(boardDate);
            if (item.getRowOrder() == null || item.getRowOrder() < 1) {
                item.setRowOrder(row);
            }
            row++;
        }

        List<IssueBoardItem> savedItems = new ArrayList<>(repository.saveAll(items));
        try {
            notificationService.sendAssignmentNotifications(boardDate, mapByRowOrder(existingItems), mapByRowOrder(savedItems));
        } catch (Exception ex) {
            log.error("Failed to send assignment notifications for boardDate={} — save was still successful", boardDate, ex);
        }
        return savedItems;
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
