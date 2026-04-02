package org.example.service;

import org.example.entity.TrainingScheduleItem;
import org.example.repository.TrainingScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class TrainingScheduleService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("MMM''yy", Locale.ENGLISH);

    private final TrainingScheduleRepository repository;

    public TrainingScheduleService(TrainingScheduleRepository repository) {
        this.repository = repository;
    }

    public List<TrainingScheduleItem> getLatest() {
        Optional<TrainingScheduleItem> latest = repository.findTopByOrderByConfigDateDescIdDesc();
        if (latest.isEmpty() || latest.get().getPeriodLabel() == null) {
            return Collections.emptyList();
        }

        return repository.findByPeriodLabelOrderByRowOrderAscIdAsc(latest.get().getPeriodLabel());
    }

    public List<TrainingScheduleItem> getByPeriod(String periodLabel) {
        return repository.findByPeriodLabelOrderByRowOrderAscIdAsc(periodLabel);
    }

    public List<String> getPeriods() {
        return repository.findDistinctPeriodLabelsDesc();
    }

    @Transactional
    public List<TrainingScheduleItem> replaceByDate(LocalDate configDate, List<TrainingScheduleItem> rows) {
        String periodLabel = PERIOD_FORMATTER.format(configDate);
        repository.deleteByPeriodLabel(periodLabel);

        int rowOrder = 1;
        for (TrainingScheduleItem row : rows) {
            row.setId(null);
            row.setConfigDate(configDate);
            row.setPeriodLabel(periodLabel);
            if (row.getRowOrder() == null || row.getRowOrder() < 1) {
                row.setRowOrder(rowOrder);
            }
            rowOrder++;
        }

        return repository.saveAll(rows);
    }
}
