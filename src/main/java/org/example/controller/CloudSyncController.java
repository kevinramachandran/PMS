package org.example.controller;

import org.example.service.CloudSyncService;
import org.example.service.DataSyncService;
import org.example.service.AttachmentStorageService;
import org.example.util.RoleAccess;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.HashMap;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cloud-sync")
public class CloudSyncController {
    private final CloudSyncService service;
    private final AttachmentStorageService attachments;
    public CloudSyncController(CloudSyncService service) { this(service, null); }
    @org.springframework.beans.factory.annotation.Autowired
    public CloudSyncController(CloudSyncService service, AttachmentStorageService attachments) { this.service = service; this.attachments = attachments; }

    @GetMapping("/identity")
    public Map<String, String> identity() { return Map.of("instanceId", service.installationId()); }

    /** Accept report upserts over the authenticated outbound session established by On-Prem. */
    @PostMapping("/push")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> push(@RequestBody Map<String, Object> payload,
            @org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") String requestKey,
            HttpSession session) throws IOException {
        requireSyncPeer(session);
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 200)
            throw new IllegalArgumentException("A valid idempotency key is required for cloud pushes");
        String peerId = String.valueOf(payload.getOrDefault("peerInstanceId", ""));
        String dataset = String.valueOf(payload.getOrDefault("dataset", ""));
        String category = String.valueOf(payload.getOrDefault("category", ""));
        com.fasterxml.jackson.databind.JsonNode inputRecords = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(payload.get("records"));
        if (!(inputRecords instanceof com.fasterxml.jackson.databind.node.ArrayNode records))
            throw new IllegalArgumentException("Push request must contain a JSON records array");
        Map<Long, Long> knownIds = new HashMap<>();
        if (payload.get("knownIds") instanceof Map<?, ?> ids) for (var entry : ids.entrySet()) {
            try { knownIds.put(Long.valueOf(String.valueOf(entry.getKey())), Long.valueOf(String.valueOf(entry.getValue()))); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("Push contains an invalid record ID mapping"); }
        }
        return service.acceptPeerPush(requestKey, peerId, dataset, category, records, knownIds);
    }

    @GetMapping("/data/{dataset}")
    public DataSyncService.JsonSyncExport exportData(@PathVariable String dataset,
            @RequestParam(defaultValue = "") String category, HttpSession session) {
        requireSyncPeer(session);
        return service.exportJsonForSync(dataset, category);
    }

    @GetMapping("/attachments/{module}/{storedName}/metadata")
    public Map<String, Object> attachmentMetadata(@PathVariable String module, @PathVariable String storedName, HttpSession session) throws IOException {
        requireSyncPeer(session);
        if (attachments == null) throw new IllegalStateException("Attachment storage is not configured");
        String safeModule = attachments.normalizeModule(module);
        byte[] bytes;
        String checksum;
        try {
            bytes = attachments.readImage(safeModule, storedName);
            checksum = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.io.FileNotFoundException ex) {
            return Map.of("missing", true);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
        return Map.of("checksum", checksum, "size", bytes.length);
    }

    @PutMapping(value = "/attachments/{module}/{storedName}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> putAttachment(@PathVariable String module, @PathVariable String storedName,
            @org.springframework.web.bind.annotation.RequestBody byte[] bytes, HttpSession session) throws IOException {
        requireSyncPeer(session);
        if (attachments == null) throw new IllegalStateException("Attachment storage is not configured");
        attachments.storeSyncedImage(module, storedName, bytes);
        return ResponseEntity.ok(Map.of("status", "success", "storedName", storedName));
    }

    /** Sync-only image download; it does not depend on the user's page view permissions. */
    @GetMapping("/attachments/{module}/{storedName}")
    public ResponseEntity<byte[]> getAttachment(@PathVariable String module, @PathVariable String storedName,
            HttpSession session) throws IOException {
        requireSyncPeer(session);
        if (attachments == null) throw new IllegalStateException("Attachment storage is not configured");
        try {
            byte[] bytes = attachments.readImage(module, storedName);
            return ResponseEntity.ok()
                    .contentType(attachments.resolveMediaType(storedName))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + attachments.sanitizeFilename(storedName) + "\"")
                    .body(bytes);
        } catch (java.io.FileNotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IOException ex) {
            org.slf4j.LoggerFactory.getLogger(CloudSyncController.class)
                    .error("Cloud sync attachment read failed for module {} and filename {}", module, storedName, ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    private static void requireSyncPeer(HttpSession session) {
        Object allowed = session == null ? null : session.getAttribute("syncConfigurationUser");
        String role = session == null ? null : (String) session.getAttribute("role");
        if (!Boolean.TRUE.equals(allowed) && !RoleAccess.isAdmin(role))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Cloud sync account required");
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        var c = service.getOrCreate();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("cloudUrl", c.getCloudUrl());
        result.put("cloudUsername", c.getCloudUsername());
        result.put("pushReportsEnabled", c.isPushReportsEnabled());
        result.put("downloadFolder", c.getDownloadFolder());
        result.put("processingFolder", c.getProcessingFolder());
        result.put("failedFolder", c.getFailedFolder());
        result.put("delaySeconds", c.getDelaySeconds());
        result.put("intervalMinutes", c.getIntervalMinutes());
        result.put("intervalStartTime", c.getIntervalStartTime());
        result.put("enabled", c.isEnabled());
        result.put("scheduleTime", c.getScheduleTime());
        result.put("scheduleMode", c.getScheduleMode());
        result.put("scheduleTimes", c.getScheduleTimes());
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
    public Map<String, Object> run(@RequestParam(required = false) String dataset) {
        service.startNow(dataset);
        return Map.of("status", "success", "message", "Sync started");
    }
}
