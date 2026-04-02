package org.example.service;

import org.example.entity.IssueBoardItem;
import org.example.repository.IssueBoardItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class IssueBoardItemService {

    private final IssueBoardItemRepository repository;

    public IssueBoardItemService(IssueBoardItemRepository repository) {
        this.repository = repository;
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

        return repository.saveAll(items);
    }
}
