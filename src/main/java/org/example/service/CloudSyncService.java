package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.example.entity.SyncConfiguration;
import org.example.repository.SyncConfigurationRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
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
    @org.springframework.beans.factory.annotation.Autowired
    private AttachmentStorageService attachmentStorage;
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
        String startTime = input.containsKey("intervalStartTime")
                ? scheduleTime(input.get("intervalStartTime")) : config.getIntervalStartTime();
        boolean resetAnchor = startTime != null && (config.getIntervalAnchorAt() == null
                || !startTime.equals(config.getIntervalStartTime())
                || config.getIntervalMinutes() != intervalMinutes(input.getOrDefault("intervalMinutes", config.getIntervalMinutes()))
                || (!"INTERVAL".equals(config.getScheduleMode()) && "INTERVAL".equals(input.get("scheduleMode"))));
        config.setCloudUrl(required(input, "cloudUrl"));
        config.setCloudUsername(required(input, "cloudUsername"));
        String password = Objects.toString(input.get("cloudPassword"), "");
        if (!password.isBlank()) config.setEncryptedCloudPassword(secrets.encrypt(password));
        config.setDownloadFolder(required(input, "downloadFolder"));
        config.setProcessingFolder(required(input, "processingFolder"));
        config.setFailedFolder(required(input, "failedFolder"));
        config.setDelaySeconds(number(input, "delaySeconds", 60, 0, 86400));
        config.setIntervalMinutes(intervalMinutes(input.getOrDefault("intervalMinutes", config.getIntervalMinutes())));
        config.setEnabled(Boolean.parseBoolean(String.valueOf(input.getOrDefault("enabled", false))));
        config.setDatasets(String.join("\n", SyncDatasetPlan.resolve(String.valueOf(input.getOrDefault("datasets", config.getDatasets())))));
        config.setScheduleTime(scheduleTime(input.getOrDefault("scheduleTime", config.getScheduleTime())));
        config.setScheduleMode(scheduleMode(input.getOrDefault("scheduleMode", config.getScheduleMode())));
        config.setScheduleTimes(scheduleTimes(input.getOrDefault("scheduleTimes", config.getScheduleTimes())));
        validateSettings(config);
        config.setIntervalStartTime(startTime);
        if (resetAnchor) config.setIntervalAnchorAt(LocalDateTime.now().toLocalDate().atTime(LocalTime.parse(startTime)));
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
        if ("INTERVAL".equals(config.getScheduleMode())) {
            LocalDateTime anchor = config.getIntervalAnchorAt();
            if (anchor != null) {
                if (now.isBefore(anchor)) return false;
                long elapsedMinutes = java.time.Duration.between(anchor, now).toMinutes();
                LocalDateTime due = anchor.plusMinutes((elapsedMinutes / config.getIntervalMinutes()) * config.getIntervalMinutes());
                return config.getLastRunAt() == null || config.getLastRunAt().isBefore(due);
            }
            return config.getLastRunAt() == null
                    || !now.isBefore(config.getLastRunAt().plusMinutes(config.getIntervalMinutes()));
        }
        if ("TIMES".equals(config.getScheduleMode())) {
            return Arrays.stream(config.getScheduleTimes().split(","))
                    .map(time -> now.toLocalDate().atTime(LocalTime.parse(time.trim())))
                    .anyMatch(due -> !now.isBefore(due)
                            && (config.getLastRunAt() == null || config.getLastRunAt().isBefore(due)));
        }
        LocalDateTime due = now.toLocalDate().atTime(LocalTime.parse(config.getScheduleTime()));
        return !now.isBefore(due) && (config.getLastRunAt() == null || config.getLastRunAt().isBefore(due));
    }

    public void startNow() {
        startNow(null);
    }

    public void startNow(String datasetSelection) {
        SyncConfiguration config = getOrCreate();
        List<String> selectedDatasets = datasetSelection == null || datasetSelection.isBlank()
                ? null : SyncDatasetPlan.resolve(datasetSelection);
        if (claimRun(config)) java.util.concurrent.CompletableFuture.runAsync(() -> execute(config, selectedDatasets));
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
        execute(config, null);
    }

    private void execute(SyncConfiguration config, List<String> selectedDatasets) {
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
            List<String> plan = selectedDatasets == null
                    ? SyncDatasetPlan.resolve(config.getDatasets()) : selectedDatasets;
            if (selectedDatasets == null) {
                config.setDatasets(String.join("\n", plan));
                repository.save(config);
            }
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
            ImportSummary summary = processFiles(config, batch, sourceWarnings, runId, client);
            config.setLastStatus(summary.hasWarnings() ? "SUCCESS_WITH_WARNINGS" : "SUCCESS");
            config.setLastSuccessAt(LocalDateTime.now());
            String message = "Sync completed. Downloaded " + downloaded + " dataset file(s). " + summary.message()
                    + " CSV files cleared from sync folders.";
            config.setLastMessage(message.substring(0, Math.min(message.length(), 2000)));
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            config.setLastStatus("ERROR");
            String message = ex.getMessage() == null ? "Sync failed" : ex.getMessage();
            config.setLastMessage(message.substring(0, Math.min(message.length(), 2000)));
        } finally {
            try {
                cleanupSyncCsvFiles(config);
            } catch (Exception cleanupError) {
                String cleanupMessage = "CSV cleanup failed: " + Objects.toString(cleanupError.getMessage(), "unknown error");
                if ("SUCCESS".equals(config.getLastStatus())) {
                    config.setLastStatus("SUCCESS_WITH_WARNINGS");
                }
                String existingMessage = Objects.toString(config.getLastMessage(), "");
                String combinedMessage = existingMessage.isBlank() ? cleanupMessage : existingMessage + " " + cleanupMessage;
                config.setLastMessage(combinedMessage.substring(0, Math.min(combinedMessage.length(), 2000)));
            }
            try { repository.save(config); }
            finally { running.set(false); }
        }
    }

    String processEligible(SyncConfiguration config, boolean manualRun) throws IOException {
        Path download = Path.of(config.getDownloadFolder());
        long cutoff = manualRun ? Long.MAX_VALUE : System.currentTimeMillis() - config.getDelaySeconds() * 1000L;
        try (var files = Files.list(download)) {
            return processFiles(config, files.filter(p -> p.toString().endsWith(".csv"))
                    .filter(p -> p.toFile().lastModified() <= cutoff).toList(), List.of(), UUID.randomUUID().toString(), null).message();
        }
    }

    private record ImportSummary(String message, boolean hasWarnings) { }

    private ImportSummary processFiles(SyncConfiguration config, List<Path> batch, List<String> sourceWarnings, String runId,
                                       HttpClient attachmentClient) throws IOException {
        Path processing = Path.of(config.getProcessingFolder());
        Path failed = Path.of(config.getFailedFolder());
        Files.createDirectories(processing); Files.createDirectories(failed);
        List<String> errors = new ArrayList<>();
        Set<String> warnings = new LinkedHashSet<>(sourceWarnings);
        List<String> skippedFiles = new ArrayList<>();
        List<ImportedSnapshot> importedSnapshots = new ArrayList<>();
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
                    String csv = Files.readString(staged);
                    downloadReferencedAttachments(attachmentClient, config, dataset, csv);
                    if (dataset.equals("users")) {
                        // Preflight the entire snapshot and replace in one database transaction.
                        dataSync.replaceUsersForCloudSync(csv);
                        importedSnapshots.add(new ImportedSnapshot(dataset, category, csv));
                        Files.deleteIfExists(staged);
                        continue;
                    }
                    Map<String, Object> result = dataSync.importCsvForSync(dataset, category, csv);
                    created += ((Number) result.getOrDefault("created", 0)).intValue();
                    updated += ((Number) result.getOrDefault("updated", 0)).intValue();
                    unchanged += ((Number) result.getOrDefault("unchanged", 0)).intValue();
                    if (result.get("warnings") instanceof Collection<?> items) items.forEach(item -> warnings.add(dataset + (category.isBlank() ? "" : ":" + category) + ": " + item));
                    importedSnapshots.add(new ImportedSnapshot(dataset, category, Files.readString(staged)));
                    Files.deleteIfExists(staged);
                } catch (Exception ex) {
                    Files.move(staged, failed.resolve(staged.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    errors.add(staged.getFileName() + ": " + Objects.toString(ex.getMessage(), "Import failed"));
                }
            }
        }
        if (!errors.isEmpty()) throw new IOException("Sync import failed for " + errors.size() + " file(s). "
                + String.join("; ", errors) + ". " + skippedFiles.size() + " dependent file(s) not imported.");
        int deleted = 0;
        // Delete dependants before their master data, after every cloud file has imported successfully.
        for (ImportedSnapshot snapshot : importedSnapshots.stream()
                .sorted(Comparator.comparingInt((ImportedSnapshot s) -> SyncDatasetPlan.priority(s.dataset(), s.category())).reversed()).toList()) {
            if (!snapshot.dataset().equals("users"))
                deleted += dataSync.deleteMissingForSync(snapshot.dataset(), snapshot.category(), snapshot.csv());
        }
        return new ImportSummary(created + " added, " + updated + " replaced, " + unchanged + " unchanged, " + deleted + " deleted. "
                + warnings.size() + " warning(s). "
                + warnings.stream().limit(3).collect(java.util.stream.Collectors.joining("; ")), !warnings.isEmpty());
    }

    private void downloadReferencedAttachments(HttpClient client, SyncConfiguration config, String dataset, String csv)
            throws IOException {
        if (attachmentStorage == null || client == null) return; // Folder-only processing has no authenticated cloud session.
        String module = switch (dataset) {
            case "abnormality" -> "abnormality-reporting";
            case "gemba-walk", "gemba-kaizen", "process-confirmation" -> dataset;
            default -> "";
        };
        if (module.isBlank()) return;
        Set<String> filenames = referencedAttachmentNames(csv);
        for (String filename : filenames) {
            String endpoint = url(config) + "/api/attachments/" + module + "/file/"
                    + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            HttpResponse<byte[]> response;
            try {
                response = client.send(HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(java.time.Duration.ofMinutes(2)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while downloading cloud attachment " + filename, ex);
            }
            if (response.statusCode() / 100 != 2)
                throw new IOException("Cloud attachment " + module + "/" + filename + " could not be downloaded (HTTP "
                        + response.statusCode() + "). Check cloud account view permissions and confirm the source file exists.");
            attachmentStorage.storeSyncedImage(module, filename, response.body());
        }
    }

    static Set<String> referencedAttachmentNames(String csv) throws IOException {
        if (csv.startsWith("\ufeff")) csv = csv.substring(1);
        Set<String> names = new LinkedHashSet<>();
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setAllowMissingColumnNames(false).setDuplicateHeaderMode(org.apache.commons.csv.DuplicateHeaderMode.DISALLOW)
                .build().parse(new StringReader(csv))) {
            for (CSVRecord record : parser) {
                if (!record.isConsistent()) throw new IOException("Cloud CSV has an inconsistent row while reading attachment references");
                for (String column : parser.getHeaderNames()) {
                    String value = DataSyncService.unprotectCell(record.get(column)).trim();
                    if (value.isBlank()) continue;
                    if (isAttachmentColumn(column)) names.add(value);
                    else if (isAttachmentJsonColumn(column)) {
                        JsonNode root;
                        try { root = new ObjectMapper().readTree(value); }
                        catch (IOException ex) { throw new IOException("Invalid attachment metadata in cloud column " + column, ex); }
                        if (root == null) continue;
                        if (root.isArray()) for (JsonNode item : root) addAttachmentNames(item, names);
                        else addAttachmentNames(root, names);
                    }
                }
            }
        }
        return names;
    }

    private static boolean isAttachmentColumn(String name) {
        return name.equals("pictureImage") || name.equals("observationImage") || name.endsWith("ObservationImage");
    }

    private static boolean isAttachmentJsonColumn(String name) {
        return name.equals("observations") || name.equals("zmObservationsJson")
                || name.equals("pmObservationsJson") || name.equals("qmObservationsJson");
    }

    private static void addAttachmentNames(JsonNode node, Set<String> names) {
        for (String field : List.of("pictureImage", "observationImage")) {
            String value = node.path(field).asText("").trim();
            if (!value.isBlank()) names.add(value);
        }
    }

    private record ImportedSnapshot(String dataset, String category, String csv) { }

    private static void cleanupSyncCsvFiles(SyncConfiguration config) throws IOException {
        for (Path folder : cleanupFolders(config)) {
            if (!Files.isDirectory(folder)) continue;
            try (var files = Files.list(folder)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
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

    private static String scheduleMode(Object value) {
        String mode = Objects.toString(value, "").trim();
        if (!Set.of("DAILY", "INTERVAL", "TIMES").contains(mode))
            throw new IllegalArgumentException("Choose daily, interval or specific sync times");
        return mode;
    }

    private static int intervalMinutes(Object value) {
        try {
            int minutes = Integer.parseInt(String.valueOf(value));
            if (minutes >= 1 && minutes <= 1440) return minutes;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Sync interval must be between 1 and 1440 minutes");
    }

    private static String scheduleTimes(Object value) {
        String times = Objects.toString(value, "").trim();
        if (times.isBlank() || times.length() > 2000)
            throw new IllegalArgumentException("Enter specific sync times in HH:mm format, separated by commas");
        return Arrays.stream(times.split(",", -1)).map(CloudSyncService::scheduleTime)
                .distinct().sorted().collect(java.util.stream.Collectors.joining(","));
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
            if (!paths.add(resolved)) throw new IllegalArgumentException("Download, processing and failed folders must be different");
        }
        SyncDatasetPlan.resolve(c.getDatasets());
        scheduleTime(c.getScheduleTime());
        scheduleMode(c.getScheduleMode());
        intervalMinutes(c.getIntervalMinutes());
        scheduleTimes(c.getScheduleTimes());
    }
    private static List<Path> folders(SyncConfiguration c) {
        return List.of(Path.of(c.getDownloadFolder()), Path.of(c.getProcessingFolder()), Path.of(c.getFailedFolder()));
    }
    private static List<Path> cleanupFolders(SyncConfiguration c) {
        List<Path> result = new ArrayList<>(folders(c));
        if (c.getCompletedFolder() != null && !c.getCompletedFolder().isBlank()) {
            result.add(Path.of(c.getCompletedFolder()));
        }
        return result;
    }
    private static String required(Map<String, Object> input, String key) { String value = Objects.toString(input.get(key), "").trim(); if (value.isBlank()) throw new IllegalArgumentException(key + " is required"); return value; }
    private static int number(Map<String, Object> input, String key, int fallback, int min, int max) { int value; try { value = Integer.parseInt(String.valueOf(input.getOrDefault(key, fallback))); } catch (NumberFormatException ex) { value = fallback; } return Math.max(min, Math.min(max, value)); }
}
