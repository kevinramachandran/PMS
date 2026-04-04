package org.example.service;

import org.example.entity.LsrDailyTracking;
import org.example.repository.LsrDailyTrackingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LsrDailyTrackingService {

    private final LsrDailyTrackingRepository repository;

    public LsrDailyTrackingService(LsrDailyTrackingRepository repository) {
        this.repository = repository;
    }

    public List<LsrDailyTracking> getByYearAndMonth(int year, int month) {
        return repository.findByYearAndMonth(year, month);
    }

    public LsrDailyTracking save(LsrDailyTracking data) {
        repository.findByYearAndMonthAndDay(data.getYear(), data.getMonth(), data.getDay())
                .ifPresent(existing -> data.setId(existing.getId()));
        return repository.save(data);
    }

    public List<LsrDailyTracking> saveAll(List<LsrDailyTracking> dataList) {
        dataList.forEach(data ->
            repository.findByYearAndMonthAndDay(data.getYear(), data.getMonth(), data.getDay())
                    .ifPresent(existing -> data.setId(existing.getId()))
        );
        return repository.saveAll(dataList);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
