package org.example.controller;

import org.example.service.CloudRoutingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/master-cloud-target")
public class MasterCloudTargetController {
    private final CloudRoutingService routing;
    public MasterCloudTargetController(CloudRoutingService routing) { this.routing = routing; }
    @GetMapping
    public ResponseEntity<?> target(@RequestParam String config) {
        if (!Set.of("kpi-plant-name", "master-designation", "master-abnormality", "master-gemba-walk",
                "master-gemba-kaizen", "master-process", "user-management").contains(config)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Unsupported cloud-managed page"));
        }
        try { return ResponseEntity.ok(Map.of("cloudUrl", routing.validatedCloudUrl())); }
        catch (IllegalArgumentException ex) { return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage())); }
    }
}
