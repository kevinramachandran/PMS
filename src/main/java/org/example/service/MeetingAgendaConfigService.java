package org.example.service;

import org.example.entity.MeetingAgendaConfig;
import org.example.repository.MeetingAgendaConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class MeetingAgendaConfigService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("MMM''yy", Locale.ENGLISH);

    private final MeetingAgendaConfigRepository repository;

    public MeetingAgendaConfigService(MeetingAgendaConfigRepository repository) {
        this.repository = repository;
    }

    public Optional<MeetingAgendaConfig> getLatest() {
        return repository.findTopByOrderByConfigDateDescIdDesc();
    }

    public Optional<MeetingAgendaConfig> getByPeriod(String periodLabel) {
        return repository.findByPeriodLabel(periodLabel);
    }

    public List<String> getPeriods() {
        return repository.findDistinctPeriodLabelsDesc();
    }

    @Transactional
    public MeetingAgendaConfig replaceByDate(LocalDate configDate, MeetingAgendaConfig payload) {
        String periodLabel = PERIOD_FORMATTER.format(configDate);
        repository.deleteByPeriodLabel(periodLabel);

        payload.setId(null);
        payload.setConfigDate(configDate);
        payload.setPeriodLabel(periodLabel);

        return repository.save(payload);
    }
}
