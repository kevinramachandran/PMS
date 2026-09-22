package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.service.CloudSyncService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cloud-sync")
public class CloudSyncController {
    private final CloudSyncService service;
    public CloudSyncController(CloudSyncService service) { this.service = service; }

    @GetMapping("/config")
    public Map<String, Object> config() {
        var c = service.getOrCreate();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("cloudUrl", c.getCloudUrl());
        result.put("cloudUsername", c.getCloudUsername());
        result.put("downloadFolder", c.getDownloadFolder());
        result.put("processingFolder", c.getProcessingFolder());
        result.put("completedFolder", c.getCompletedFolder());
        result.put("failedFolder", c.getFailedFolder());
        result.put("delaySeconds", c.getDelaySeconds());
        result.put("intervalMinutes", c.getIntervalMinutes());
        result.put("enabled", c.isEnabled());
        result.put("scheduleTime", c.getScheduleTime());
        result.put("datasets", c.getDatasets());
        result.put("effectiveDatasets", c.getDatasets().isBlank() ? java.util.List.of()
                : org.example.service.SyncDatasetPlan.resolve(c.getDatasets()));
        return result;
    }

    @PutMapping("/config")
    public Map<String, Object> save(@RequestBody Map<String, Object> payload) {
        service.save(payload);
        return Map.of("status", "success", "message", "Sync configuration saved");
    }

    @GetMapping("/status")
    public Map<String, Object> status() { return service.status(); }

    @PostMapping("/run")
    public Map<String, Object> run() {
        service.startNow();
        return Map.of("status", "success", "message", "Sync started");
    }
}
