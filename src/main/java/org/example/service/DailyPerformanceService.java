package org.example.service;

import org.example.entity.DailyPerformance;
import org.example.repository.DailyPerformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

@Service
public class DailyPerformanceService {

    private final DailyPerformanceRepository repository;

    public DailyPerformanceService(DailyPerformanceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DailyPerformance saveOrUpdateToday(DailyPerformance input) {
        if (input.getFields() != null) {
            if (input.getFields().size() > 30 || input.getFields().stream().anyMatch(field ->
                    field == null || field.label() == null || field.label().isBlank() || field.label().length() > 120
                    || (field.unit() != null && field.unit().length() > 40)
                    || field.value() == null || field.value().isBlank() || field.value().length() > 200)) {
                throw new IllegalArgumentException("Provide up to 30 fields with a name and value.");
            }
        } else if (input.getMonthTarget() == null || input.getActualMtd() == null
                || input.getDailyTarget() == null || input.getYesterday() == null) {
            throw new IllegalArgumentException("Daily performance values are required.");
        }
        LocalDate today = LocalDate.now();
        DailyPerformance existing = repository.findByDate(today).orElseGet(DailyPerformance::new);

        org.example.util.DeletionGuard.checkRemoval(input.getFields() != null && existing.getFields() != null && input.getFields().size() < existing.getFields().size());
        existing.setDate(today);
        existing.setMonthTarget(input.getMonthTarget() != null ? input.getMonthTarget() : (existing.getMonthTarget() != null ? existing.getMonthTarget() : 0.0));
        existing.setActualMtd(input.getActualMtd() != null ? input.getActualMtd() : (existing.getActualMtd() != null ? existing.getActualMtd() : 0.0));
        existing.setDailyTarget(input.getDailyTarget() != null ? input.getDailyTarget() : (existing.getDailyTarget() != null ? existing.getDailyTarget() : 0.0));
        existing.setYesterday(input.getYesterday() != null ? input.getYesterday() : (existing.getYesterday() != null ? existing.getYesterday() : 0.0));

        if (input.getFields() != null) existing.setFields(new java.util.ArrayList<>(input.getFields()));
        return repository.save(existing);
    }

    public Optional<DailyPerformance> getLatest() {
        return repository.findTopByOrderByDateDesc();
    }

    public Optional<DailyPerformance> getToday() {
        return repository.findByDate(LocalDate.now());
    }

    public Optional<DailyPerformance> getLatestCurrentMonth() {
        YearMonth currentMonth = YearMonth.now();
        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();
        return repository.findTopByDateBetweenOrderByDateDesc(start, end);
    }

    public Optional<DailyPerformance> getLatestByMonth(int month, int year) {
        return getLatestByMonth(month, year, null);
    }

    public Optional<DailyPerformance> getLatestByMonth(int month, int year, LocalDate asOf) {
        YearMonth selectedMonth = YearMonth.of(year, month);
        LocalDate start = selectedMonth.atDay(1);
        LocalDate end = selectedMonth.atEndOfMonth();
        if (asOf != null) {
            if (asOf.isBefore(start)) {
                end = start;
            } else if (asOf.isBefore(end)) {
                end = asOf;
            }
        }
        return repository.findTopByDateBetweenOrderByDateDesc(start, end);
    }
}
