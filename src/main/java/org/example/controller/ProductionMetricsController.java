package org.example.controller;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.ProductionMetrics;
import org.example.service.ProductionMetricsService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/production-metrics")
public class ProductionMetricsController {

    private final ProductionMetricsService service;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<List<ProductionMetrics>> getAllRecords() {
        return ResponseEntity.ok(service.getAllRecords());
    }

    @GetMapping(params = "date")
    public ResponseEntity<?> getRecordByDateQuery(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return service.getRecordByDate(date)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductionMetrics> getRecordById(@PathVariable Long id) {
        return service.getRecordById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<ProductionMetrics> getRecordByDate(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime date) {
        return service.getRecordByDate(date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<ProductionMetrics>> getRecordsByDateRange(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate) {
        return ResponseEntity.ok(service.getRecordsByDateRange(startDate, endDate));
    }

    @GetMapping("/current-month")
    public ResponseEntity<List<ProductionMetrics>> getCurrentMonthData() {
        return ResponseEntity.ok(service.getCurrentMonthData());
    }

    @GetMapping("/by-day/{date}")
    public ResponseEntity<ProductionMetrics> getRecordByDay(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return service.getRecordByDay(date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/month")
    public ResponseEntity<List<ProductionMetrics>> getRecordsByMonth(
            @RequestParam int month,
            @RequestParam int year) {
        if (month < 1 || month > 12) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(service.getRecordsByMonth(month, year));
    }

    @PostMapping
    public ResponseEntity<ProductionMetrics> createRecord(@RequestBody ProductionMetrics metrics) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.createRecord(metrics));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductionMetrics> updateRecord(
            @PathVariable Long id,
            @RequestBody ProductionMetrics metrics) {
        try {
            return ResponseEntity.ok(service.updateRecord(id, metrics));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/upsert")
    public ResponseEntity<ProductionMetrics> upsertByDate(@RequestBody ProductionMetrics metrics) {
        try {
            return ResponseEntity.ok(service.upsertByDate(metrics));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/{date}")
    public ResponseEntity<ProductionMetrics> patchByDate(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestBody Map<String, Object> patchPayload) {
        try {
            Map<String, Object> normalized = normalizePatchPayload(patchPayload);
            ProductionMetrics patch = objectMapper.convertValue(normalized, ProductionMetrics.class);
            return ResponseEntity.ok(service.patchRecordByDate(date, patch));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private Map<String, Object> normalizePatchPayload(Map<String, Object> source) {
        Map<String, Object> normalized = new HashMap<>();
        if (source == null) {
            return normalized;
        }

        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            normalized.put(toCamelCase(key), entry.getValue());
        }

        return normalized;
    }

    private String toCamelCase(String key) {
        if (!key.contains("_")) {
            return key;
        }

        StringBuilder out = new StringBuilder();
        boolean upperNext = false;
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if (ch == '_') {
                upperNext = true;
                continue;
            }

            if (upperNext) {
                out.append(Character.toUpperCase(ch));
                upperNext = false;
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    @PutMapping("/bulk-update")
    public ResponseEntity<List<ProductionMetrics>> bulkUpdate(@RequestBody List<ProductionMetrics> updates) {
        try {
            return ResponseEntity.ok(service.bulkUpdateRecords(updates));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecord(@PathVariable Long id) {
        service.deleteRecord(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/date/{date}")
    public ResponseEntity<Void> deleteByDate(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime date) {
        service.deleteByDate(date);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/all")
    public ResponseEntity<Void> deleteAllRecords() {
        service.deleteAllRecords();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export/csv")
    public ResponseEntity<Resource> exportToCSV() {
        try {
            ByteArrayInputStream data = service.exportToCSV();
            InputStreamResource resource = new InputStreamResource(data);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=production_metrics.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/import/csv")
    public ResponseEntity<String> importFromCSV(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please upload a CSV file");
        }

        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest().body("Please upload a valid CSV file");
        }

        try {
            service.importFromCSV(file);
            return ResponseEntity.ok("CSV file imported successfully!");
        } catch (IOException e) {
            e.printStackTrace();
            String message = e.getMessage();
            if (message != null && message.contains("Successfully imported")) {
                // Partial success - some records imported
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .body("Partial import completed. " + message);
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to import CSV file: " + message);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing CSV file: " + e.getMessage());
        }
    }
}
