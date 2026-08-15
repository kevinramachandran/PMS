package org.example.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public DashboardConfigController(KpiFooterButtonsConfigService kpiFooterButtonsConfigService,
                                     ObjectMapper objectMapper) {
        this.kpiFooterButtonsConfigService = kpiFooterButtonsConfigService;
        this.objectMapper = objectMapper;
    }

    private static final String KPI_DECK_TITLE_PREFIX = "PMS 4 deck V0_";

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
        String plantName = kpiFooterButtonsConfigService.getPlantName();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deckTitle", KPI_DECK_TITLE_PREFIX + plantName);
        payload.put("plantName", plantName);
        payload.put("lsrOverviewTarget", lsrOverviewTarget);
        payload.put("lsrTarget12", lsrTarget12);
        payload.put("lsrTarget5", lsrTarget5);
        payload.put("kpiButton1Label", footerButtons.getButton1Label());
        payload.put("kpiButton1Url", footerButtons.getButton1Url());
        payload.put("kpiButton1Type", footerButtons.getButton1Type() != null ? footerButtons.getButton1Type() : "link");
        payload.put("kpiButton2Label", footerButtons.getButton2Label());
        payload.put("kpiButton2Url", footerButtons.getButton2Url());
        payload.put("kpiButton2Type", footerButtons.getButton2Type() != null ? footerButtons.getButton2Type() : "link");
        payload.put("buttons", getInfoPortalButtons(footerButtons));

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

    @GetMapping("/info-portal")
    public Map<String, Object> getInfoPortalConfig() {
        KpiFooterButtonsConfig config = kpiFooterButtonsConfigService.getOrCreateDefaults();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("button1Label", config.getButton1Label());
        payload.put("button1Url", config.getButton1Url());
        payload.put("button1Type", config.getButton1Type() != null ? config.getButton1Type() : "link");
        payload.put("button1FileName", config.getButton1FileName() != null ? config.getButton1FileName() : "");
        payload.put("button2Label", config.getButton2Label());
        payload.put("button2Url", config.getButton2Url());
        payload.put("button2Type", config.getButton2Type() != null ? config.getButton2Type() : "link");
        payload.put("button2FileName", config.getButton2FileName() != null ? config.getButton2FileName() : "");
        payload.put("buttons", getInfoPortalButtons(config));
        return payload;
    }

    @GetMapping("/plant-name")
    public Map<String, Object> getPlantNameConfig() {
        String plantName = kpiFooterButtonsConfigService.getPlantName();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("prefix", KPI_DECK_TITLE_PREFIX);
        payload.put("plantName", plantName);
        payload.put("deckTitle", KPI_DECK_TITLE_PREFIX + plantName);
        return payload;
    }

    @PostMapping("/plant-name")
    public Map<String, Object> savePlantNameConfig(@RequestBody Map<String, Object> request) {
        String plantName = request.get("plantName") == null ? "" : String.valueOf(request.get("plantName"));
        KpiFooterButtonsConfig saved = kpiFooterButtonsConfigService.savePlantName(plantName);
        String savedPlantName = saved.getPlantName() == null || saved.getPlantName().isBlank()
                ? KpiFooterButtonsConfigService.DEFAULT_PLANT_NAME
                : saved.getPlantName().trim();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "success");
        payload.put("prefix", KPI_DECK_TITLE_PREFIX);
        payload.put("plantName", savedPlantName);
        payload.put("deckTitle", KPI_DECK_TITLE_PREFIX + savedPlantName);
        return payload;
    }

    @PostMapping("/info-portal")
    public Map<String, Object> saveInfoPortalConfig(@RequestBody Map<String, Object> request) {
        List<Map<String, Object>> buttons = normalizeRequestedButtons(request.get("buttons"), kpiFooterButtonsConfigService.getOrCreateDefaults());
        KpiFooterButtonsConfig incoming = new KpiFooterButtonsConfig();
        Map<String, Object> first = buttons.size() > 0 ? buttons.get(0) : Map.of();
        Map<String, Object> second = buttons.size() > 1 ? buttons.get(1) : Map.of();
        incoming.setButton1Label(asString(first.get("label")));
        incoming.setButton1Url("file".equals(asString(first.get("type"))) ? "" : asString(first.get("url")));
        incoming.setButton1Type(defaultType(asString(first.get("type"))));
        incoming.setButton2Label(asString(second.get("label")));
        incoming.setButton2Url("file".equals(asString(second.get("type"))) ? "" : asString(second.get("url")));
        incoming.setButton2Type(defaultType(asString(second.get("type"))));
        incoming.setButtonsJson(writeButtonsJson(buttons));

        KpiFooterButtonsConfig saved = kpiFooterButtonsConfigService.save(incoming);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("button1Label", saved.getButton1Label());
        payload.put("button1Url", saved.getButton1Url());
        payload.put("button1Type", saved.getButton1Type() != null ? saved.getButton1Type() : "link");
        payload.put("button1FileName", saved.getButton1FileName() != null ? saved.getButton1FileName() : "");
        payload.put("button2Label", saved.getButton2Label());
        payload.put("button2Url", saved.getButton2Url());
        payload.put("button2Type", saved.getButton2Type() != null ? saved.getButton2Type() : "link");
        payload.put("button2FileName", saved.getButton2FileName() != null ? saved.getButton2FileName() : "");
        payload.put("buttons", getInfoPortalButtons(saved));
        return payload;
    }

    @PostMapping(value = "/info-portal/upload/{buttonNum}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadInfoPortalFile(
            @PathVariable int buttonNum,
            @RequestParam("file") MultipartFile file) {

        if (buttonNum < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid button number."));
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
            List<Map<String, Object>> buttons = getInfoPortalButtons(config);
            Map<String, Object> button = findOrCreateButton(buttons, buttonNum);
            deleteOldFile(uploadPath, asString(button.get("file")));
            button.put("type", "file");
            button.put("file", storedName);
            button.put("fileName", sanitizeFilename(originalFilename));
            button.put("url", "");
            config.setButtonsJson(writeButtonsJson(buttons));

            if (buttonNum == 1) {
                deleteOldFile(uploadPath, config.getButton1File());
                config.setButton1File(storedName);
                config.setButton1FileName(sanitizeFilename(originalFilename));
                config.setButton1Type("file");
            } else if (buttonNum == 2) {
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

    @GetMapping("/info-portal/file/{buttonNum}")
    public ResponseEntity<byte[]> serveInfoPortalFile(@PathVariable int buttonNum) {
        if (buttonNum < 1) {
            return ResponseEntity.notFound().build();
        }

        KpiFooterButtonsConfig config = kpiFooterButtonsConfigService.getOrCreateDefaults();
        Map<String, Object> button = findButton(getInfoPortalButtons(config), buttonNum);
        String storedName = asString(button.get("file"));
        String displayName = asString(button.get("fileName"));

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

    private List<Map<String, Object>> getInfoPortalButtons(KpiFooterButtonsConfig config) {
        if (config.getButtonsJson() != null && !config.getButtonsJson().isBlank()) {
            try {
                List<Map<String, Object>> parsed = objectMapper.readValue(config.getButtonsJson(), new TypeReference<>() {});
                if (parsed != null && !parsed.isEmpty()) {
                    return normalizeRequestedButtons(parsed, config);
                }
            } catch (JsonProcessingException ignored) { }
        }

        List<Map<String, Object>> buttons = new java.util.ArrayList<>();
        addLegacyButton(buttons, 1, config.getButton1Label(), config.getButton1Url(), config.getButton1Type(), config.getButton1File(), config.getButton1FileName());
        addLegacyButton(buttons, 2, config.getButton2Label(), config.getButton2Url(), config.getButton2Type(), config.getButton2File(), config.getButton2FileName());
        return buttons;
    }

    private void addLegacyButton(List<Map<String, Object>> buttons, int id, String label, String url, String type, String file, String fileName) {
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("id", id);
        button.put("label", label == null ? "" : label);
        button.put("type", defaultType(type));
        button.put("url", "file".equals(defaultType(type)) ? "" : asString(url));
        button.put("file", file == null ? "" : file);
        button.put("fileName", fileName == null ? "" : fileName);
        buttons.add(button);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeRequestedButtons(Object rawButtons, KpiFooterButtonsConfig existing) {
        List<Map<String, Object>> source = rawButtons instanceof List<?> ? (List<Map<String, Object>>) rawButtons : getInfoPortalButtons(existing);
        List<Map<String, Object>> normalized = new java.util.ArrayList<>();
        int fallbackId = 1;
        for (Map<String, Object> raw : source) {
            if (raw == null) {
                continue;
            }
            int id = toPositiveInt(raw.get("id"), fallbackId);
            fallbackId = Math.max(fallbackId + 1, id + 1);
            String type = defaultType(asString(raw.get("type")));
            Map<String, Object> button = new LinkedHashMap<>();
            button.put("id", id);
            button.put("label", asString(raw.get("label")));
            button.put("type", type);
            button.put("url", "file".equals(type) ? "" : asString(raw.get("url")));
            button.put("file", asString(raw.get("file")));
            button.put("fileName", asString(raw.get("fileName")));
            normalized.add(button);
        }
        return normalized;
    }

    private Map<String, Object> findButton(List<Map<String, Object>> buttons, int id) {
        return buttons.stream()
                .filter(button -> toPositiveInt(button.get("id"), -1) == id)
                .findFirst()
                .orElse(Map.of());
    }

    private Map<String, Object> findOrCreateButton(List<Map<String, Object>> buttons, int id) {
        for (Map<String, Object> button : buttons) {
            if (toPositiveInt(button.get("id"), -1) == id) {
                return button;
            }
        }
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("id", id);
        button.put("label", "");
        button.put("type", "file");
        button.put("url", "");
        button.put("file", "");
        button.put("fileName", "");
        buttons.add(button);
        return button;
    }

    private String writeButtonsJson(List<Map<String, Object>> buttons) {
        try {
            return objectMapper.writeValueAsString(buttons);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultType(String type) {
        return "file".equalsIgnoreCase(type) ? "file" : "link";
    }

    private String normalizeExternalUrl(String url) {
        String trimmed = asString(url).trim();
        if (trimmed.isBlank()) {
            return "";
        }
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("mailto:")
                || lower.startsWith("tel:")
                || lower.startsWith("/")
                || lower.startsWith("#")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private int toPositiveInt(Object value, int fallback) {
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
