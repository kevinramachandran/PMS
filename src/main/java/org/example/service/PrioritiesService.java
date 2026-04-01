package org.example.service;

import org.example.entity.Priorities;
import org.example.repository.PrioritiesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PrioritiesService {

    private final PrioritiesRepository repo;

    public PrioritiesService(PrioritiesRepository repo) {
        this.repo = repo;
    }

    // Save
    public Priorities save(Priorities data) {
        return repo.save(data);
    }

    // Get All
    public List<Priorities> getAll() {
        return repo.findAll();
    }

    // Get by ID
    public Priorities getById(Long id) {
        return repo.findById(id).orElseThrow(() -> new RuntimeException("Not Found"));
    }

    // Delete
    public void delete(Long id) {
        repo.deleteById(id);
    }

    // Get Today
    public List<Priorities> getToday() {
        return repo.findByDate(LocalDate.now());
    }

    // Get by Date
    public List<Priorities> getByDate(LocalDate date) {
        return repo.findByDate(date);
    }

    // Get by Type and Date
    public List<Priorities> getByTypeAndDate(String type, LocalDate date) {
        return repo.findByTypeAndDate(type, date);
    }

    // Get by Type
    public List<Priorities> getByType(String type) {
        return repo.findByType(type);
    }

    @Transactional
    public Priorities replaceByTypeAndDate(String type, LocalDate date, Priorities data) {
        repo.deleteByTypeAndDate(type, date);
        data.setId(null);
        data.setType(type);
        data.setDate(date);
        return repo.save(data);
    }
}
