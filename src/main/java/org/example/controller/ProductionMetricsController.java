package org.example.controller;

import lombok.RequiredArgsConstructor;
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
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/production-metrics")
@RequiredArgsConstructor
public class ProductionMetricsController {

    private final ProductionMetricsService service;

    @GetMapping
    public ResponseEntity<List<ProductionMetrics>> getAllRecords() {
        return ResponseEntity.ok(service.getAllRecords());
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

    @PostMapping
    public ResponseEntity<ProductionMetrics> createRecord(@RequestBody ProductionMetrics metrics) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.createRecord(metrics));
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
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/upsert")
    public ResponseEntity<ProductionMetrics> upsertByDate(@RequestBody ProductionMetrics metrics) {
        return ResponseEntity.ok(service.upsertByDate(metrics));
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
