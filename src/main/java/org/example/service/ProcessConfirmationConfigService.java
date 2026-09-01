package org.example.service;

import org.example.entity.ProcessConfirmationConfig;
import org.example.repository.ProcessConfirmationConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class ProcessConfirmationConfigService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("MMM''yy", Locale.ENGLISH);

    private final ProcessConfirmationConfigRepository repository;
    private final ProcessMasterDataService processMasterDataService;

    public ProcessConfirmationConfigService(ProcessConfirmationConfigRepository repository,
                                            ProcessMasterDataService processMasterDataService) {
        this.repository = repository;
        this.processMasterDataService = processMasterDataService;
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
        validateObservationQuestions(payload);
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

    private void validateObservationQuestions(ProcessConfirmationConfig payload) {
        Set<String> allowed = new HashSet<>();
        allowed.addAll(processMasterDataService.names(ProcessMasterDataService.ZM_OBSERVATION));
        allowed.addAll(processMasterDataService.names(ProcessMasterDataService.PM_OBSERVATION));
        allowed.addAll(processMasterDataService.names(ProcessMasterDataService.QM_OBSERVATION));

        List<String> questions = List.of(
                normalizeQuestion(payload.getQuestion1()),
                normalizeQuestion(payload.getQuestion2()),
                normalizeQuestion(payload.getQuestion3()),
                normalizeQuestion(payload.getQuestion4()),
                normalizeQuestion(payload.getQuestion5()),
                normalizeQuestion(payload.getQuestion6()),
                normalizeQuestion(payload.getQuestion7()),
                normalizeQuestion(payload.getQuestion8()),
                normalizeQuestion(payload.getQuestion9()),
                normalizeQuestion(payload.getQuestion10())
        );

        for (String question : questions) {
            if (!question.isEmpty() && !allowed.contains(question)) {
                throw new IllegalArgumentException("Process confirmation observations must use configured Process Master Data values");
            }
        }
    }

    private String normalizeQuestion(String value) {
        return value == null ? "" : value.trim();
    }
}
