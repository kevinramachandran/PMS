package org.example.service;

import org.example.entity.DailyData;
import org.example.repository.DailyDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class DailyDataService {

    private final DailyDataRepository repo;

    public DailyDataService(DailyDataRepository repo) {
        this.repo = repo;
    }

    // Save
    public DailyData save(DailyData data) {
        return repo.save(data);
    }

    // Get All
    public List<DailyData> getAll() {
        return repo.findAll();
    }

    // Get by ID
    public DailyData getById(Long id) {
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Not Found"));
    }

    // Delete
    public void delete(Long id) {
        repo.deleteById(id);
    }

    // Get Today
    public List<DailyData> getToday() {
        return repo.findByDate(LocalDate.now());
    }

    // Get by Date
    public List<DailyData> getByDate(LocalDate date) {
        return repo.findByDate(date);
    }

    // Get by Type and Date
    public List<DailyData> getByTypeAndDate(String type, LocalDate date) {
        return repo.findByTypeAndDate(type, date);
    }

    // Get by Type
    public List<DailyData> getByType(String type) {
        return repo.findByType(type);
    }

    public List<DailyData> getLatestByTypeAndMonth(String type, int month, int year) {
        YearMonth selectedMonth = YearMonth.of(year, month);
        LocalDate latestDate = repo.findLatestDateByTypeWithinRange(type, selectedMonth.atDay(1), selectedMonth.atEndOfMonth());
        if (latestDate == null) {
            return List.of();
        }
        return repo.findByTypeAndDate(type, latestDate);
    }

    @Transactional
    public List<DailyData> replaceByTypeAndDate(String type, LocalDate date, List<DailyData> dataList) {
        repo.deleteByTypeAndDate(type, date);

        for (DailyData data : dataList) {
            data.setId(null);
            data.setType(type);
            data.setDate(date);
        }

        return repo.saveAll(dataList);
    }
}
