package org.example.controller;

import org.example.entity.DailyPerformance;
import org.example.service.DailyPerformanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        if (payload.getMonthTarget() == null || payload.getActualMtd() == null ||
                payload.getDailyTarget() == null || payload.getYesterday() == null) {
            return ResponseEntity.badRequest().build();
        }

        DailyPerformance saved = service.saveOrUpdateToday(payload);
        return ResponseEntity.ok(saved);
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
            @RequestParam int year) {
        if (month < 1 || month > 12) {
            return ResponseEntity.badRequest().build();
        }

        return service.getLatestByMonth(month, year)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
