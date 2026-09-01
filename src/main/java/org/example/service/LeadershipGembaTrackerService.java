package org.example.service;

import org.example.entity.LeadershipGembaTrackerEntry;
import org.example.repository.LeadershipGembaTrackerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class LeadershipGembaTrackerService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("MMM''yy", Locale.ENGLISH);

    private final LeadershipGembaTrackerRepository repository;
    private final PlantMasterDataService plantMasterDataService;

    public LeadershipGembaTrackerService(LeadershipGembaTrackerRepository repository,
                                         PlantMasterDataService plantMasterDataService) {
        this.repository = repository;
        this.plantMasterDataService = plantMasterDataService;
    }

    public List<LeadershipGembaTrackerEntry> getLatest() {
        Optional<LeadershipGembaTrackerEntry> latest = repository.findTopByOrderByScheduleDateDescIdDesc();
        if (latest.isEmpty() || latest.get().getPeriodLabel() == null) {
            return Collections.emptyList();
        }
        return repository.findByPeriodLabelOrderByRowOrderAscIdAsc(latest.get().getPeriodLabel());
    }

    public List<LeadershipGembaTrackerEntry> getByPeriodLabel(String periodLabel) {
        return repository.findByPeriodLabelOrderByRowOrderAscIdAsc(periodLabel);
    }

    public List<String> getAvailablePeriods() {
        return repository.findDistinctPeriodLabelsDesc();
    }

    @Transactional
    public List<LeadershipGembaTrackerEntry> replaceByDate(LocalDate scheduleDate, List<LeadershipGembaTrackerEntry> items) {
        String periodLabel = PERIOD_FORMATTER.format(scheduleDate);
        repository.deleteByPeriodLabel(periodLabel);

        int row = 1;
        for (LeadershipGembaTrackerEntry item : items) {
            item.setId(null);
            item.setScheduleDate(scheduleDate);
            item.setPeriodLabel(periodLabel);
            if (item.getRowOrder() == null || item.getRowOrder() < 1) {
                item.setRowOrder(row);
            }
            validateConfigured(item.getDepartment(), PlantMasterDataService.DEPARTMENT, "Department");
            validateConfigured(item.getAreaOfCoverage(), PlantMasterDataService.PROCESS_AREA, "Area of Coverage");
            row++;
        }

        return repository.saveAll(items);
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
