package org.example.controller;

import org.example.entity.KpiFooterButtonsConfig;
import org.example.service.KpiFooterButtonsConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard-config")
@CrossOrigin
public class DashboardConfigController {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "png", "jpg", "jpeg");

    private final KpiFooterButtonsConfigService kpiFooterButtonsConfigService;

    public DashboardConfigController(KpiFooterButtonsConfigService kpiFooterButtonsConfigService) {
        this.kpiFooterButtonsConfigService = kpiFooterButtonsConfigService;
    }

    @Value("${dashboard.kpi.deck-title:PMS 4 deck V0_Dharuhera Brewery}")
    private String deckTitle;

    @Value("${dashboard.kpi.lsr.overview-target:85%}")
    private String lsrOverviewTarget;

    @Value("${dashboard.kpi.lsr.target-12:Target 50%}")
    private String lsrTarget12;

    @Value("${dashboard.kpi.lsr.target-5:Target 100%}")
    private String lsrTarget5;

    @Value("${app.upload.footer-buttons.dir:./uploads/footer-buttons}")
    private String uploadDir;

    @GetMapping("/kpi")
    public Map<String, Object> getKpiDashboardConfig() {
        KpiFooterButtonsConfig footerButtons = kpiFooterButtonsConfigService.getOrCreateDefaults();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deckTitle", deckTitle);
        payload.put("lsrOverviewTarget", lsrOverviewTarget);
        payload.put("lsrTarget12", lsrTarget12);
        payload.put("lsrTarget5", lsrTarget5);
        payload.put("kpiButton1Label", footerButtons.getButton1Label());
        payload.put("kpiButton1Url", footerButtons.getButton1Url());
        payload.put("kpiButton1Type", footerButtons.getButton1Type() != null ? footerButtons.getButton1Type() : "link");
        payload.put("kpiButton2Label", footerButtons.getButton2Label());
        payload.put("kpiButton2Url", footerButtons.getButton2Url());
        payload.put("kpiButton2Type", footerButtons.getButton2Type() != null ? footerButtons.getButton2Type() : "link");

        payload.put("lsrFocusRules", List.of(
            List.of(
                "Slow down when Pedestrian in 2-3 truck length.",
                "Use LOTO when servicing or repairing equipment's",
                "Use PPE/harness while working at height",
                "Verify Oxygen content before entering",
                "ESI/PF (Legal documents) to be checked"
            ),
            List.of(
                "Never block walkways and fire exits",
                "Place locks and warning labels",
                "Rope of work areas post warning signs",
                "Never enter Confined space without work permit",
                "PPE adherence to be checked"
            ),
            List.of(
                "Follow speed limits",
                "Check the presence and working condition of protective devices",
                "Never access roof/fragile surface without work permit",
                "Never enter Confined space without Watchmen",
                "Work Permit adherence to be checked"
            )
        ));

        return payload;
    }

    @GetMapping("/kpi-footer-buttons")
    public Map<String, String> getKpiFooterButtonsConfig() {
        KpiFooterButtonsConfig config = kpiFooterButtonsConfigService.getOrCreateDefaults();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("button1Label", config.getButton1Label());
        payload.put("button1Url", config.getButton1Url());
        payload.put("button1Type", config.getButton1Type() != null ? config.getButton1Type() : "link");
        payload.put("button1FileName", config.getButton1FileName() != null ? config.getButton1FileName() : "");
        payload.put("button2Label", config.getButton2Label());
        payload.put("button2Url", config.getButton2Url());
        payload.put("button2Type", config.getButton2Type() != null ? config.getButton2Type() : "link");
        payload.put("button2FileName", config.getButton2FileName() != null ? config.getButton2FileName() : "");
        return payload;
    }

    @PostMapping("/kpi-footer-buttons")
    public Map<String, String> saveKpiFooterButtonsConfig(@RequestBody Map<String, String> request) {
        KpiFooterButtonsConfig incoming = new KpiFooterButtonsConfig();
        incoming.setButton1Label(request.getOrDefault("button1Label", ""));
        incoming.setButton1Url(request.getOrDefault("button1Url", ""));
        incoming.setButton1Type(request.getOrDefault("button1Type", "link"));
        incoming.setButton2Label(request.getOrDefault("button2Label", ""));
        incoming.setButton2Url(request.getOrDefault("button2Url", ""));
        incoming.setButton2Type(request.getOrDefault("button2Type", "link"));

        KpiFooterButtonsConfig saved = kpiFooterButtonsConfigService.save(incoming);
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("button1Label", saved.getButton1Label());
        payload.put("button1Url", saved.getButton1Url());
        payload.put("button1Type", saved.getButton1Type() != null ? saved.getButton1Type() : "link");
        payload.put("button1FileName", saved.getButton1FileName() != null ? saved.getButton1FileName() : "");
        payload.put("button2Label", saved.getButton2Label());
        payload.put("button2Url", saved.getButton2Url());
        payload.put("button2Type", saved.getButton2Type() != null ? saved.getButton2Type() : "link");
        payload.put("button2FileName", saved.getButton2FileName() != null ? saved.getButton2FileName() : "");
        return payload;
    }

    @PostMapping(value = "/kpi-footer-buttons/upload/{buttonNum}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadButtonFile(
            @PathVariable int buttonNum,
            @RequestParam("file") MultipartFile file) {

        if (buttonNum != 1 && buttonNum != 2) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid button number. Must be 1 or 2."));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty."));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid filename."));
        }

        String ext = getExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "File type not allowed. Allowed: PDF, Word, Excel, PowerPoint, PNG, JPG."));
        }

        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String storedName = UUID.randomUUID().toString() + "." + ext;
            Path targetPath = uploadPath.resolve(storedName);

            Files.write(targetPath, file.getBytes());

            KpiFooterButtonsConfig config = kpiFooterButtonsConfigService.getOrCreateDefaults();
            if (buttonNum == 1) {
                deleteOldFile(uploadPath, config.getButton1File());
                config.setButton1File(storedName);
                config.setButton1FileName(sanitizeFilename(originalFilename));
                config.setButton1Type("file");
            } else {
                deleteOldFile(uploadPath, config.getButton2File());
                config.setButton2File(storedName);
                config.setButton2FileName(sanitizeFilename(originalFilename));
                config.setButton2Type("file");
            }
            kpiFooterButtonsConfigService.saveEntity(config);

            return ResponseEntity.ok(Map.of(
                "originalName", sanitizeFilename(originalFilename),
                "storedName", storedName
            ));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to save file. Please try again."));
        }
    }

    @GetMapping("/kpi-footer-buttons/file/{buttonNum}")
    public ResponseEntity<byte[]> serveButtonFile(@PathVariable int buttonNum) {
        if (buttonNum != 1 && buttonNum != 2) {
            return ResponseEntity.notFound().build();
        }

        KpiFooterButtonsConfig config = kpiFooterButtonsConfigService.getOrCreateDefaults();
        String storedName = buttonNum == 1 ? config.getButton1File() : config.getButton2File();
        String displayName = buttonNum == 1 ? config.getButton1FileName() : config.getButton2FileName();

        if (storedName == null || storedName.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = uploadPath.resolve(storedName).normalize();

            // Security: prevent path traversal
            if (!filePath.startsWith(uploadPath)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] data = Files.readAllBytes(filePath);
            MediaType mediaType = resolveMediaType(storedName);
            String safeDisplayName = (displayName != null && !displayName.isBlank()) ? displayName : storedName;

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeDisplayName + "\"")
                    .body(data);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ---- Helpers ----

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1) ? filename.substring(dot + 1) : "";
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9.\\-_ ]", "_");
    }

    private void deleteOldFile(Path uploadPath, String existingStoredName) {
        if (existingStoredName == null || existingStoredName.isBlank()) return;
        try {
            Path old = uploadPath.resolve(existingStoredName).normalize();
            if (old.startsWith(uploadPath)) {
                Files.deleteIfExists(old);
            }
        } catch (IOException ignored) { }
    }

    private MediaType resolveMediaType(String filename) {
        String ext = getExtension(filename).toLowerCase();
        return switch (ext) {
            case "pdf"  -> MediaType.APPLICATION_PDF;
            case "png"  -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "docx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "doc"  -> MediaType.parseMediaType("application/msword");
            case "xlsx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "xls"  -> MediaType.parseMediaType("application/vnd.ms-excel");
            case "pptx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
            case "ppt"  -> MediaType.parseMediaType("application/vnd.ms-powerpoint");
            default     -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
