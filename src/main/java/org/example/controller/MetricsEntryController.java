package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.model.MetricsEntryBundlePayload;
import org.example.model.MetricsEntryPayload;
import org.example.service.ProductionMetricsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@CrossOrigin
@RequiredArgsConstructor
@RequestMapping({"/api/metrics", "/metrics"})
public class MetricsEntryController {

    private final ProductionMetricsService productionMetricsService;

    @GetMapping
    public ResponseEntity<?> getMetricsByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            return productionMetricsService.getMetricsEntryBundle(date)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.noContent().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> saveMetricsEntry(@RequestBody MetricsEntryBundlePayload payload) {
        try {
            return ResponseEntity.ok(productionMetricsService.saveMetricsEntryBundle(payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
