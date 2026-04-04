package org.example.service;

import org.example.entity.HsDailyCross;
import org.example.repository.HsDailyCrossRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HsDailyCrossService {

    private final HsDailyCrossRepository repository;

    public HsDailyCrossService(HsDailyCrossRepository repository) {
        this.repository = repository;
    }

    public List<HsDailyCross> getByYearAndMonth(int year, int month) {
        return repository.findByYearAndMonth(year, month);
    }

    public HsDailyCross save(HsDailyCross data) {
        repository.findByYearAndMonthAndDay(data.getYear(), data.getMonth(), data.getDay())
                .ifPresent(existing -> data.setId(existing.getId()));
        return repository.save(data);
    }

    public List<HsDailyCross> saveAll(List<HsDailyCross> dataList) {
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
