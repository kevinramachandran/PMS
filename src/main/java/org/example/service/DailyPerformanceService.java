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
        LocalDate today = LocalDate.now();
        DailyPerformance existing = repository.findByDate(today).orElseGet(DailyPerformance::new);

        existing.setDate(today);
        existing.setMonthTarget(input.getMonthTarget());
        existing.setActualMtd(input.getActualMtd());
        existing.setDailyTarget(input.getDailyTarget());
        existing.setYesterday(input.getYesterday());

        return repository.save(existing);
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
        YearMonth selectedMonth = YearMonth.of(year, month);
        LocalDate start = selectedMonth.atDay(1);
        LocalDate end = selectedMonth.atEndOfMonth();
        return repository.findTopByDateBetweenOrderByDateDesc(start, end);
    }
}
