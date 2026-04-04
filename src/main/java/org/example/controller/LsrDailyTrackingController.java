package org.example.controller;

import org.example.entity.LsrDailyTracking;
import org.example.service.LsrDailyTrackingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lsr-daily")
@CrossOrigin
public class LsrDailyTrackingController {

    private final LsrDailyTrackingService service;

    public LsrDailyTrackingController(LsrDailyTrackingService service) {
        this.service = service;
    }

    @GetMapping("/{year}/{month}")
    public List<LsrDailyTracking> getByYearAndMonth(
            @PathVariable int year,
            @PathVariable int month) {
        return service.getByYearAndMonth(year, month);
    }

    @PostMapping
    public LsrDailyTracking save(@RequestBody LsrDailyTracking data) {
        return service.save(data);
    }

    @PostMapping("/batch")
    public List<LsrDailyTracking> saveBatch(@RequestBody List<LsrDailyTracking> dataList) {
        return service.saveAll(dataList);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "Deleted";
    }
}
