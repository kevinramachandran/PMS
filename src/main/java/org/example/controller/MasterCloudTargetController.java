package org.example.controller;

import org.example.service.CloudSyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/master-cloud-target")
public class MasterCloudTargetController {
    private static final Set<String> MASTER_CONFIGS = Set.of(
            "kpi-plant-name", "master-designation", "master-abnormality",
            "master-gemba-walk", "master-gemba-kaizen", "master-process", "user-management");
    private final CloudSyncService cloudSyncService;

    public MasterCloudTargetController(CloudSyncService cloudSyncService) {
        this.cloudSyncService = cloudSyncService;
    }

    @GetMapping
    public Map<String, String> target(@RequestParam String config) {
        if (!MASTER_CONFIGS.contains(config)) throw new IllegalArgumentException("Unsupported master data page");
        String cloudUrl = cloudSyncService.getOrCreate().getCloudUrl();
        return Map.of("cloudUrl", cloudUrl == null ? "" : cloudUrl);
    }
}
