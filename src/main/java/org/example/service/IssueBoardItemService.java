package org.example.service;

import org.example.entity.IssueBoardItem;
import org.example.repository.IssueBoardItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final IssueBoardNotificationService notificationService;

    public IssueBoardItemService(IssueBoardItemRepository repository,
                                 IssueBoardNotificationService notificationService) {
        this.repository = repository;
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
}
