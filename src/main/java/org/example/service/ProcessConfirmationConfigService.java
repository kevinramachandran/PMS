package org.example.service;

import org.example.entity.ProcessConfirmationConfig;
import org.example.repository.ProcessConfirmationConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ProcessConfirmationConfigService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("MMM''yy", Locale.ENGLISH);

    private final ProcessConfirmationConfigRepository repository;

    public ProcessConfirmationConfigService(ProcessConfirmationConfigRepository repository) {
        this.repository = repository;
    }

    public Optional<ProcessConfirmationConfig> getLatest() {
        return repository.findTopByOrderByConfigDateDescIdDesc();
    }

    public Optional<ProcessConfirmationConfig> getByPeriod(String periodLabel) {
        return repository.findByPeriodLabel(periodLabel);
    }

    public List<String> getPeriods() {
        return repository.findDistinctPeriodLabelsDesc();
    }

    @Transactional
    public ProcessConfirmationConfig replaceByDate(LocalDate configDate, ProcessConfirmationConfig payload) {
        String periodLabel = PERIOD_FORMATTER.format(configDate);
        repository.deleteByPeriodLabel(periodLabel);

        payload.setId(null);
        payload.setConfigDate(configDate);
        payload.setPeriodLabel(periodLabel);

        if (payload.getMonthLabel() == null || payload.getMonthLabel().isBlank()) {
            payload.setMonthLabel(periodLabel);
        }

        return repository.save(payload);
    }
}
