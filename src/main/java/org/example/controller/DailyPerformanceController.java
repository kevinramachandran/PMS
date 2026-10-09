package org.example.controller;

import org.example.entity.DailyPerformance;
import org.example.service.DailyPerformanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/daily-performance")
@CrossOrigin
public class DailyPerformanceController {

    private final DailyPerformanceService service;

    public DailyPerformanceController(DailyPerformanceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DailyPerformance> save(@RequestBody DailyPerformance payload) {
        try {
            return ResponseEntity.ok(service.saveOrUpdateToday(payload));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/latest")
    public ResponseEntity<DailyPerformance> getLatest() {
        return service.getLatest().map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/today")
    public ResponseEntity<DailyPerformance> getToday() {
        return service.getToday()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/current-month")
    public ResponseEntity<DailyPerformance> getCurrentMonthLatest() {
        return service.getLatestCurrentMonth()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/month")
    public ResponseEntity<DailyPerformance> getMonthLatest(
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate asOf) {
        if (month < 1 || month > 12) {
            return ResponseEntity.badRequest().build();
        }

        return service.getLatestByMonth(month, year, asOf)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
