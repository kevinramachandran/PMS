package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.SyncConfiguration;
import org.example.repository.SyncConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CloudSyncService {
    private final SyncConfigurationRepository repository;
    private final SyncSecretService secrets;
    private final DataSyncService dataSync;
    private final ObjectMapper mapper;
    private final AtomicBoolean running = new AtomicBoolean();

    public CloudSyncService(SyncConfigurationRepository repository, SyncSecretService secrets,
            DataSyncService dataSync, ObjectMapper mapper) {
        this.repository = repository;
        this.secrets = secrets;
        this.dataSync = dataSync;
        this.mapper = mapper;
    }

    public synchronized SyncConfiguration getOrCreate() {
        return repository.findAll().stream().findFirst().orElseGet(() -> repository.save(new SyncConfiguration()));
    }

    @Transactional
    public synchronized SyncConfiguration save(Map<String, Object> input) {
        if (running.get()) throw new IllegalArgumentException("Wait for the current sync to finish before changing its settings");
        SyncConfiguration config = getOrCreate();
        config.setCloudUrl(required(input, "cloudUrl"));
        config.setCloudUsername(required(input, "cloudUsername"));
        String password = Objects.toString(input.get("cloudPassword"), "");
        if (!password.isBlank()) config.setEncryptedCloudPassword(secrets.encrypt(password));
        config.setDownloadFolder(required(input, "downloadFolder"));
        config.setProcessingFolder(required(input, "processingFolder"));
        config.setCompletedFolder(required(input, "completedFolder"));
        config.setFailedFolder(required(input, "failedFolder"));
        config.setDelaySeconds(number(input, "delaySeconds", 60, 0, 86400));
        config.setIntervalMinutes(number(input, "intervalMinutes", 15, 1, 1440));
        config.setEnabled(Boolean.parseBoolean(String.valueOf(input.getOrDefault("enabled", false))));
        config.setDatasets(String.join("\n", SyncDatasetPlan.resolve(String.valueOf(input.getOrDefault("datasets", config.getDatasets())))));
        config.setScheduleTime(scheduleTime(input.getOrDefault("scheduleTime", config.getScheduleTime())));
        validateSettings(config);
        return repository.save(config);
    }

    public Map<String, Object> status() {
        SyncConfiguration c = getOrCreate();
        return Map.of("status", c.getLastStatus(), "message", c.getLastMessage(),
                "configured", isConfigured(c),
                "lastRunAt", Objects.toString(c.getLastRunAt(), ""),
                "lastSuccessAt", Objects.toString(c.getLastSuccessAt(), ""));
    }

    public void runNow() { run(getOrCreate()); }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${app.sync.poll-ms:60000}")
    public void scheduledSync() {
        SyncConfiguration config = getOrCreate();
        if (!config.isEnabled()) return;
        if (isDue(config, LocalDateTime.now())) run(config);
    }

    static boolean isDue(SyncConfiguration config, LocalDateTime now) {
        LocalDateTime due = now.toLocalDate().atTime(LocalTime.parse(config.getScheduleTime()));
        return !now.isBefore(due) && (config.getLastRunAt() == null || config.getLastRunAt().isBefore(due));
    }

    public void startNow() {
        SyncConfiguration config = getOrCreate();
        if (claimRun(config)) java.util.concurrent.CompletableFuture.runAsync(() -> execute(config));
    }

    private void run(SyncConfiguration config) {
        if (claimRun(config)) execute(config);
    }

    private synchronized boolean claimRun(SyncConfiguration config) {
        if (!running.compareAndSet(false, true)) return false;
        try {
            config.setLastRunAt(LocalDateTime.now());
            config.setLastStatus("RUNNING");
            config.setLastMessage("Preparing sync...");
            repository.save(config);
            return true;
        } catch (RuntimeException ex) {
            running.set(false);
            throw ex;
        }
    }

    private void execute(SyncConfiguration config) {
        try {
            validateConfigured(config);
            for (Path folder : folders(config)) {
                Files.createDirectories(folder);
                Path probe = Files.createTempFile(folder, ".sync-write-check-", ".tmp");
                Files.delete(probe);
            }
            config.setLastStatus("RUNNING");
            config.setLastMessage("Connecting to cloud...");
            repository.save(config);
            HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(20))
                    .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ORIGINAL_SERVER)).build();
            login(client, config);
            int downloaded = 0;
            List<Path> batch = new ArrayList<>();
            List<String> sourceWarnings = new ArrayList<>();
            String runId = UUID.randomUUID().toString();
            List<String> plan = SyncDatasetPlan.resolve(config.getDatasets());
            config.setDatasets(String.join("\n", plan));
            repository.save(config);
            for (String datasetSpec : plan) {
                if (datasetSpec.isBlank()) continue;
                String[] parts = datasetSpec.trim().split(":", 2);
                String dataset = parts[0];
                String category = parts.length == 2 ? parts[1] : "";
                Path folder = Path.of(config.getDownloadFolder());
                Files.createDirectories(folder);
                var exported = export(client, config, dataset, category);
                byte[] csv = exported.csv().getBytes(StandardCharsets.UTF_8);
                exported.warnings().forEach(w -> sourceWarnings.add(datasetSpec + ": " + w));
                String name = dataset + "__" + category + "__" + runId + ".csv";
                Path temp = folder.resolve(name + ".part");
                Files.write(temp, csv, StandardOpenOption.CREATE_NEW);
                try { Files.move(temp, folder.resolve(name), StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ex) { Files.move(temp, folder.resolve(name)); }
                batch.add(folder.resolve(name));
                downloaded++;
            }
            ImportSummary summary = processFiles(config, batch, sourceWarnings, runId);
            config.setLastStatus(summary.hasWarnings() ? "SUCCESS_WITH_WARNINGS" : "SUCCESS");
            config.setLastSuccessAt(LocalDateTime.now());
            String message = "Sync completed. Downloaded " + downloaded + " dataset file(s). " + summary.message();
            config.setLastMessage(message.substring(0, Math.min(message.length(), 2000)));
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            config.setLastStatus("ERROR");
            String message = ex.getMessage() == null ? "Sync failed" : ex.getMessage();
            config.setLastMessage(message.substring(0, Math.min(message.length(), 2000)));
        } finally {
            try { repository.save(config); }
            finally { running.set(false); }
        }
    }

    String processEligible(SyncConfiguration config, boolean manualRun) throws IOException {
        Path download = Path.of(config.getDownloadFolder());
        long cutoff = manualRun ? Long.MAX_VALUE : System.currentTimeMillis() - config.getDelaySeconds() * 1000L;
        try (var files = Files.list(download)) {
            return processFiles(config, files.filter(p -> p.toString().endsWith(".csv"))
                    .filter(p -> p.toFile().lastModified() <= cutoff).toList(), List.of(), UUID.randomUUID().toString()).message();
        }
    }

    private record ImportSummary(String message, boolean hasWarnings) { }

    private ImportSummary processFiles(SyncConfiguration config, List<Path> batch, List<String> sourceWarnings, String runId) throws IOException {
        Path processing = Path.of(config.getProcessingFolder());
        Path completed = Path.of(config.getCompletedFolder());
        Path failed = Path.of(config.getFailedFolder());
        Files.createDirectories(processing); Files.createDirectories(completed); Files.createDirectories(failed);
        List<String> errors = new ArrayList<>();
        Set<String> warnings = new LinkedHashSet<>(sourceWarnings);
        List<Map<String, Object>> results = new ArrayList<>();
        List<String> skippedFiles = new ArrayList<>();
        int created = 0, updated = 0, unchanged = 0;
        {
            List<Path> eligible = batch.stream()
                    .sorted(Comparator.comparingInt(CloudSyncService::importPriority).thenComparing(p -> p.getFileName().toString())).toList();
            for (Path file : eligible) {
                if (!errors.isEmpty()) {
                    eligible.subList(eligible.indexOf(file), eligible.size()).forEach(p -> skippedFiles.add(p.getFileName().toString()));
                    break;
                }
                Path staged = Files.move(file, processing.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                try {
                    String[] parts = parseDatasetAndCategory(staged.getFileName().toString());
                    String dataset = parts[0];
                    String category = parts.length > 1 ? parts[1] : "";
                    Map<String, Object> result = dataSync.importCsvForSync(dataset, category, Files.readString(staged));
                    results.add(Map.of("file", staged.getFileName().toString(), "result", result));
                    created += ((Number) result.getOrDefault("created", 0)).intValue();
                    updated += ((Number) result.getOrDefault("updated", 0)).intValue();
                    unchanged += ((Number) result.getOrDefault("unchanged", 0)).intValue();
                    if (result.get("warnings") instanceof Collection<?> items) items.forEach(item -> warnings.add(dataset + (category.isBlank() ? "" : ":" + category) + ": " + item));
                    Files.move(staged, completed.resolve(staged.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ex) {
                    Files.move(staged, failed.resolve(staged.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    errors.add(staged.getFileName() + ": " + Objects.toString(ex.getMessage(), "Import failed"));
                }
            }
        }
        Path report = completed.resolve("sync-report-" + runId + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(report.toFile(), Map.of(
                "runId", runId, "files", results, "warnings", warnings, "errors", errors, "skippedFiles", skippedFiles,
                "created", created, "updated", updated, "unchanged", unchanged));
        if (!errors.isEmpty()) throw new IOException("Sync import failed for " + errors.size() + " file(s). "
                + String.join("; ", errors) + ". " + skippedFiles.size() + " dependent file(s) not imported. Report: " + report);
        return new ImportSummary(created + " added, " + updated + " replaced, " + unchanged + " unchanged. "
                + warnings.size() + " warning(s). Report: " + report + ". " + warnings.stream().limit(3).collect(java.util.stream.Collectors.joining("; ")), !warnings.isEmpty());
    }

    private static int importPriority(Path file) {
        String[] datasetParts = parseDatasetAndCategory(file.getFileName().toString());
        String dataset = datasetParts[0];
        String category = datasetParts.length > 1 ? datasetParts[1] : "";
        return SyncDatasetPlan.priority(dataset, category);
    }

    private static String[] parseDatasetAndCategory(String fileName) {
        String name = fileName.replaceFirst("\\.csv$", "");
        List<String> datasets = List.of("plant-master", "kaizen-master", "abnormality-master", "walk-master", "process-master",
                "users", "gemba-walk", "gemba-kaizen", "abnormality", "process-confirmation");
        for (String dataset : datasets) {
            String prefix = dataset + "__";
            if (name.startsWith(prefix)) {
                String remainder = name.substring(prefix.length());
                int split = remainder.indexOf("__");
                String category = split >= 0 ? remainder.substring(0, split) : remainder;
                return new String[] { dataset, category };
            }
            prefix = dataset + "_";
            if (name.startsWith(prefix)) {
                String remainder = name.substring(prefix.length());
                if (remainder.isBlank()) return new String[] { dataset, "" };
                List<String> categories = switch (dataset) {
                    case "plant-master" -> List.of("PLANT", "DEPARTMENT", "PROCESS_AREA", "DESIGNATION");
                    case "kaizen-master" -> List.of("CLASSIFICATION_OF_KAIZEN");
                    case "abnormality-master" -> List.of("ABT_TAG_TYPE", "ABNORMALITY_DEFECT_TYPE");
                    case "walk-master" -> List.of("GEMBA_CATEGORY", "LIFE_SAVER_RULE");
                    case "process-master" -> List.of("ZM_OBSERVATION", "PM_OBSERVATION", "OM_OBSERVATION", "QM_OBSERVATION");
                    default -> List.of();
                };
                for (String category : categories) {
                    if (remainder.equals(category) || remainder.startsWith(category + "_") || remainder.startsWith(category + "__")) {
                        return new String[] { dataset, category };
                    }
                }
                return new String[] { dataset, remainder };
            }
        }
        return new String[] { name, "" };
    }

    private static String scheduleTime(Object value) {
        String result = String.valueOf(value).trim();
        try { return LocalTime.parse(result).withSecond(0).withNano(0).toString(); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Schedule time must use HH:mm format"); }
    }

    private void login(HttpClient client, SyncConfiguration c) throws IOException, InterruptedException {
        String body = mapper.writeValueAsString(Map.of("username", c.getCloudUsername(), "password", secrets.decrypt(c.getEncryptedCloudPassword())));
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url(c) + "/api/auth/login"))
                .timeout(java.time.Duration.ofSeconds(60))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw cloudError("login", response.statusCode(), response.body());
        com.fasterxml.jackson.databind.JsonNode result;
        try { result = mapper.readTree(response.body()); }
        catch (IOException ex) { throw new IOException("Cloud login returned non-JSON content. Check the cloud PMS URL and proxy configuration.", ex); }
        if (result == null || !result.path("status").asText().equals("success"))
            throw cloudError("login", response.statusCode(), response.body());
        if (response.headers().allValues("set-cookie").isEmpty())
            throw new IOException("Cloud login did not return a session. Check the cloud proxy cookie configuration.");
    }

    private DataSyncService.CsvExport export(HttpClient client, SyncConfiguration c, String dataset, String category) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url(c) + "/api/data-sync/" + dataset + "/export-for-sync?category=" +
                java.net.URLEncoder.encode(category, StandardCharsets.UTF_8)))
                .timeout(java.time.Duration.ofMinutes(5)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String operation = "export " + dataset + (category.isBlank() ? "" : ":" + category);
        if (response.statusCode() / 100 != 2)
            throw cloudError(operation, response.statusCode(), response.body());
        try {
            var exported = mapper.readValue(response.body(), DataSyncService.CsvExport.class);
            if (exported.version() != 1 || exported.csv() == null || exported.warnings() == null)
                throw new IOException("Invalid sync export format");
            return exported;
        } catch (IOException ex) {
            throw new IOException("Cloud " + operation + " returned an invalid sync export. Deploy the same updated build on both servers.", ex);
        }
    }

    private IOException cloudError(String operation, int status, String body) {
        String detail = "";
        try {
            var result = mapper.readTree(body);
            if (result != null) detail = result.path("message").asText("");
        } catch (IOException ignored) { }
        if (detail.isBlank()) detail = switch (status) {
            case 401 -> "Cloud session is missing or expired";
            case 403 -> "Check the cloud account permissions and license";
            case 404 -> "Check the cloud PMS URL and deploy the sync endpoints on cloud";
            case 301, 302, 307, 308 -> "Use the final cloud PMS URL, including HTTPS and any application context path";
            default -> "Check the cloud server and proxy logs";
        };
        return new IOException("Cloud " + operation + " failed (HTTP " + status + "): " + detail);
    }

    private String url(SyncConfiguration c) { return c.getCloudUrl().replaceAll("/+$", ""); }
    private boolean isConfigured(SyncConfiguration c) {
        return !c.getCloudUrl().isBlank() && !c.getCloudUsername().isBlank()
                && !c.getEncryptedCloudPassword().isBlank() && !c.getDownloadFolder().isBlank()
                && !c.getProcessingFolder().isBlank() && !c.getCompletedFolder().isBlank()
                && !c.getFailedFolder().isBlank() && !c.getDatasets().isBlank();
    }
    private void validateConfigured(SyncConfiguration c) {
        if (!isConfigured(c)) {
            throw new IllegalStateException("Sync settings are not configured. Open Cloud Sync Configuration and save all required settings first.");
        }
        validateSettings(c);
    }
    private static void validateSettings(SyncConfiguration c) {
        URI source;
        try { source = URI.create(c.getCloudUrl()); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Cloud PMS URL is invalid"); }
        if (!("http".equalsIgnoreCase(source.getScheme()) || "https".equalsIgnoreCase(source.getScheme()))
                || source.getHost() == null || source.getUserInfo() != null || source.getQuery() != null || source.getFragment() != null)
            throw new IllegalArgumentException("Cloud PMS URL must be an HTTP or HTTPS base URL without credentials, query parameters or a fragment");
        Set<Path> paths = new HashSet<>();
        for (Path folder : folders(c)) {
            Path resolved = folder.toAbsolutePath().normalize();
            try { if (Files.exists(resolved)) resolved = resolved.toRealPath(); }
            catch (IOException ex) { throw new IllegalArgumentException("Cannot access sync folder: " + folder, ex); }
            if (!paths.add(resolved)) throw new IllegalArgumentException("Download, processing, completed and failed folders must be different");
        }
        SyncDatasetPlan.resolve(c.getDatasets());
        scheduleTime(c.getScheduleTime());
    }
    private static List<Path> folders(SyncConfiguration c) {
        return List.of(Path.of(c.getDownloadFolder()), Path.of(c.getProcessingFolder()), Path.of(c.getCompletedFolder()), Path.of(c.getFailedFolder()));
    }
    private static String required(Map<String, Object> input, String key) { String value = Objects.toString(input.get(key), "").trim(); if (value.isBlank()) throw new IllegalArgumentException(key + " is required"); return value; }
    private static int number(Map<String, Object> input, String key, int fallback, int min, int max) { int value; try { value = Integer.parseInt(String.valueOf(input.getOrDefault(key, fallback))); } catch (NumberFormatException ex) { value = fallback; } return Math.max(min, Math.min(max, value)); }
}
