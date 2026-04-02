package org.example.service;

import org.example.entity.AbnormalityTrackerEntry;
import org.example.repository.AbnormalityTrackerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AbnormalityTrackerService {

    private static final DateTimeFormatter PERIOD_FORMATTER =
            DateTimeFormatter.ofPattern("MMM''yyyy", Locale.ENGLISH);

    private final AbnormalityTrackerRepository repository;

    public AbnormalityTrackerService(AbnormalityTrackerRepository repository) {
        this.repository = repository;
    }

    public List<AbnormalityTrackerEntry> getByPeriodLabel(String periodLabel) {
        return repository.findByPeriodLabelOrderByRowOrderAscIdAsc(periodLabel);
    }

    public List<AbnormalityTrackerEntry> getLatest() {
        Optional<AbnormalityTrackerEntry> latest = repository.findTopByOrderByRecordDateDescIdDesc();
        if (latest.isEmpty() || latest.get().getPeriodLabel() == null) {
            return Collections.emptyList();
        }
        return repository.findByPeriodLabelOrderByRowOrderAscIdAsc(latest.get().getPeriodLabel());
    }

    public List<String> getAvailablePeriods() {
        return repository.findDistinctPeriodLabelsDesc();
    }

    @Transactional
    public List<AbnormalityTrackerEntry> replaceByPeriod(String periodLabel,
                                                          List<AbnormalityTrackerEntry> items) {
        repository.deleteByPeriodLabel(periodLabel);

        int row = 1;
        for (AbnormalityTrackerEntry item : items) {
            item.setId(null);
            item.setPeriodLabel(periodLabel);
            item.setRecordDate(LocalDate.now());
            if (item.getRowOrder() == null || item.getRowOrder() < 1) {
                item.setRowOrder(row);
            }
            row++;
        }

        return repository.saveAll(items);
    }
}
