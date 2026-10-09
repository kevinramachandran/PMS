package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.model.CustomMetricDefinitionPayload;
import org.example.model.MetricsEntryBundlePayload;
import org.example.model.MetricsEntryPayload;
import org.example.service.ProductionMetricsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

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

    @GetMapping("/latest-target")
    public ResponseEntity<?> getLatestTargetByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            return productionMetricsService.getLatestTargetEntryBundle(date)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.noContent().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/custom-definitions")
    public ResponseEntity<List<CustomMetricDefinitionPayload>> getCustomMetricDefinitions() {
        return ResponseEntity.ok(productionMetricsService.getCustomMetricDefinitions());
    }

    @GetMapping("/system-definitions")
    public ResponseEntity<List<CustomMetricDefinitionPayload>> getSystemMetricDefinitions() {
        return ResponseEntity.ok(productionMetricsService.getSystemMetricDefinitions());
    }

    @PutMapping("/system-definitions")
    public ResponseEntity<?> saveSystemMetricDefinitions(@RequestBody List<CustomMetricDefinitionPayload> payloads) {
        try {
            return ResponseEntity.ok(productionMetricsService.saveSystemMetricDefinitions(payloads));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/custom-definitions")
    public ResponseEntity<?> createCustomMetricDefinition(@RequestBody CustomMetricDefinitionPayload payload) {
        try {
            return ResponseEntity.ok(productionMetricsService.createCustomMetricDefinition(payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/custom-definitions/{id}")
    public ResponseEntity<?> updateCustomMetricDefinition(@PathVariable Long id, @RequestBody CustomMetricDefinitionPayload payload) {
        try {
            return ResponseEntity.ok(productionMetricsService.updateCustomMetricDefinition(id, payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/custom-definitions/{id}")
    public ResponseEntity<?> deleteCustomMetricDefinition(@PathVariable Long id) {
        try {
            productionMetricsService.deleteCustomMetricDefinition(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> saveMetricsEntry(@RequestBody MetricsEntryBundlePayload payload, jakarta.servlet.http.HttpSession session) {
        String role = (String) session.getAttribute("role");
        java.util.Set<String> edits = session.getAttribute("editPermissions") instanceof java.util.Set<?> values
                ? values.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet()) : java.util.Set.of();
        if (!org.example.util.RoleAccess.isAdmin(role) && !edits.contains(org.example.util.RoleAccess.PAGE_PRODUCTION_METRICS_DATA)) {
            for (MetricsEntryPayload entry : java.util.Arrays.asList(payload.getActual(), payload.getTarget())) {
                if (entry == null) continue;
                if (((entry.getPeople() != null && !entry.getPeople().isEmpty()) && !edits.contains("PRODUCTION_METRICS_DATA_PEOPLE"))
                        || ((entry.getQuality() != null && !entry.getQuality().isEmpty()) && !edits.contains("PRODUCTION_METRICS_DATA_QUALITY"))
                        || ((entry.getService() != null && !entry.getService().isEmpty()) && !edits.contains("PRODUCTION_METRICS_DATA_SERVICE"))
                        || ((entry.getCost() != null && !entry.getCost().isEmpty()) && !edits.contains("PRODUCTION_METRICS_DATA_COST"))) {
                    return ResponseEntity.status(403).build();
                }
            }
        }
        try {
            return ResponseEntity.ok(productionMetricsService.saveMetricsEntryBundle(payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
