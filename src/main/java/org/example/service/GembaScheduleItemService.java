package org.example.service;

import org.example.entity.GembaScheduleItem;
import org.example.repository.GembaScheduleItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class GembaScheduleItemService {

    private static final DateTimeFormatter SCHEDULE_MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM''yyyy", Locale.ENGLISH);

    private final GembaScheduleItemRepository repository;
    private final PlantMasterDataService plantMasterDataService;

    public GembaScheduleItemService(GembaScheduleItemRepository repository,
                                    PlantMasterDataService plantMasterDataService) {
        this.repository = repository;
        this.plantMasterDataService = plantMasterDataService;
    }

    public List<GembaScheduleItem> getByScheduleDate(LocalDate scheduleDate) {
        return repository.findByScheduleDateOrderByRowOrderAscIdAsc(scheduleDate);
    }

    public List<GembaScheduleItem> getLatestSchedule() {
        Optional<GembaScheduleItem> latest = repository.findTopByOrderByScheduleDateDescIdDesc();
        if (latest.isEmpty() || latest.get().getScheduleDate() == null) {
            return Collections.emptyList();
        }

        return repository.findByScheduleDateOrderByRowOrderAscIdAsc(latest.get().getScheduleDate());
    }

    public List<LocalDate> getAvailableScheduleDates() {
        return repository.findDistinctScheduleDatesDesc();
    }

    @Transactional
    public List<GembaScheduleItem> replaceByDate(LocalDate scheduleDate, List<GembaScheduleItem> items) {
        repository.deleteByScheduleDate(scheduleDate);

        String monthLabel = SCHEDULE_MONTH_FORMATTER.format(scheduleDate);

        int row = 1;
        for (GembaScheduleItem item : items) {
            item.setId(null);
            item.setScheduleDate(scheduleDate);
            item.setScheduleMonthLabel(monthLabel);
            if (item.getRowOrder() == null || item.getRowOrder() < 1) {
                item.setRowOrder(row);
            }
            if (item.getRowType() == null || item.getRowType().isBlank()) {
                item.setRowType("PERSON");
            }
            validateMasterData(item);
            row++;
        }

        return repository.saveAll(items);
    }

    private void validateMasterData(GembaScheduleItem item) {
        validateConfigured(item.getFunctionType(), PlantMasterDataService.DEPARTMENT, "Function Type");
        validateConfigured(item.getWeek1(), PlantMasterDataService.PROCESS_AREA, "Week 1");
        validateConfigured(item.getWeek2(), PlantMasterDataService.PROCESS_AREA, "Week 2");
        validateConfigured(item.getWeek3(), PlantMasterDataService.PROCESS_AREA, "Week 3");
        validateConfigured(item.getWeek4(), PlantMasterDataService.PROCESS_AREA, "Week 4");
    }

    private void validateConfigured(String value, String category, String label) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            return;
        }
        boolean configured = plantMasterDataService.names(category).stream()
                .anyMatch(option -> option != null && option.trim().equalsIgnoreCase(trimmed));
        if (!configured) {
            throw new IllegalArgumentException(label + " must be configured in Master Data");
        }
    }
}
