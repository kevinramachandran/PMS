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

    public LeadershipGembaTrackerService(LeadershipGembaTrackerRepository repository) {
        this.repository = repository;
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
            row++;
        }

        return repository.saveAll(items);
    }
}
