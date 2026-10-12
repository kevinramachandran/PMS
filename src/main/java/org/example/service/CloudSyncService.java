package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.entity.SyncConfiguration;
import org.example.repository.SyncConfigurationRepository;
import org.example.repository.SyncRecordMappingRepository;
import org.example.entity.SyncRecordMapping;
import org.example.entity.SyncPushRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CloudSyncService.class);
    private final SyncConfigurationRepository repository;
    private final SyncSecretService secrets;
    private final DataSyncService dataSync;
    private final ObjectMapper mapper;
    @org.springframework.beans.factory.annotation.Autowired
    private AttachmentStorageService attachmentStorage;
    @org.springframework.beans.factory.annotation.Autowired
    private SyncRecordMappingRepository syncMappings;
    @org.springframework.beans.factory.annotation.Autowired
    private org.example.repository.SyncPushRequestRepository pushRequests;
    private final AtomicBoolean running = new AtomicBoolean();

    public CloudSyncService(SyncConfigurationRepository repository, SyncSecretService secrets,
            DataSyncService dataSync, ObjectMapper mapper) {
        this.repository = repository;
        this.secrets = secrets;
        this.dataSync = dataSync;
        this.mapper = mapper;
    }

    public synchronized SyncConfiguration getOrCreate() {
        SyncConfiguration config = repository.findAll().stream().findFirst().orElseGet(SyncConfiguration::new);
        if (config.getInstanceId() == null || config.getInstanceId().isBlank()) config.setInstanceId(UUID.randomUUID().toString());
        return repository.save(config);
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
        config.setPushReportsEnabled(Boolean.parseBoolean(String.valueOf(input.getOrDefault("pushReportsEnabled", config.isPushReportsEnabled()))));
        String password = Objects.toString(input.get("cloudPassword"), "");
        if (!password.isBlank()) config.setEncryptedCloudPassword(secrets.encrypt(password));
        if (input.containsKey("downloadFolder")) config.setDownloadFolder(Objects.toString(input.get("downloadFolder"), ""));
        if (input.containsKey("processingFolder")) config.setProcessingFolder(Objects.toString(input.get("processingFolder"), ""));
        if (input.containsKey("failedFolder")) config.setFailedFolder(Objects.toString(input.get("failedFolder"), ""));
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

    public DataSyncService.JsonSyncExport exportJsonForSync(String dataset, String category) {
        return dataSync.exportJsonForSync(dataset, category);
    }


    public String installationId() { return getOrCreate().getInstanceId(); }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> acceptPeerPush(String requestKey, String peerInstanceId, String dataset, String category,
                                               com.fasterxml.jackson.databind.node.ArrayNode records, Map<Long, Long> knownIds) throws IOException {
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 200)
            throw new IllegalArgumentException("A valid idempotency key is required for cloud pushes");
        if (records == null || records.isEmpty() || records.size() > 5000 || mapper.writeValueAsBytes(records).length > 20 * 1024 * 1024)
            throw new IllegalArgumentException("Push JSON must contain 1 to 5000 records and be no larger than 20 MB");
        String payloadHash = sha256(peerInstanceId + "\n" + dataset + "\n" + category + "\n" + mapper.writeValueAsString(records)
                + "\n" + mapper.writeValueAsString(new TreeMap<>(knownIds)));
        var previous = pushRequests.findById(requestKey).orElse(null);
        if (previous != null) {
            if (!previous.getPayloadHash().equals(payloadHash))
                throw new IllegalArgumentException("Idempotency key was already used with a different payload");
            return mapper.readValue(previous.getResultJson(), new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
        }
        Map<String, Object> result;
        try { result = dataSync.importJsonFromPeer(peerInstanceId, dataset, category, records, knownIds); }
        catch (RuntimeException ex) {
            log.error("Cloud report push {} from peer {} failed for {}", requestKey, peerInstanceId, dataset, ex);
            throw ex;
        }
        SyncPushRequest request = new SyncPushRequest();
        request.setRequestKey(requestKey); request.setPayloadHash(payloadHash);
        request.setResultJson(mapper.writeValueAsString(result));
        pushRequests.save(request);
        log.info("Cloud report push {} from peer {} applied to {}", requestKey, peerInstanceId, dataset);
        return result;
    }

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
            config.setLastStatus("RUNNING");
            config.setLastMessage("Connecting to cloud...");
            repository.save(config);
            HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(20))
                    .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ORIGINAL_SERVER)).build();
            login(client, config);
            String cloudInstanceId = null;
            if (config.isPushReportsEnabled()) {
                cloudInstanceId = cloudIdentity(client, config);
                if (!cloudInstanceId.matches("[0-9a-fA-F-]{36}")) throw new IOException("Cloud returned an invalid installation identity");
                config.setCloudInstanceId(cloudInstanceId);
                repository.save(config);
            }
            int downloaded = 0;
            int pushed = 0;
            int legacyIdConflicts = 0;
            int attachmentFailures = 0;
            List<DataSyncService.JsonSyncExport> snapshots = new ArrayList<>();
            Set<String> warnings = new LinkedHashSet<>();
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
                DataSyncService.JsonSyncExport exported;
                try { exported = export(client, config, dataset, category); }
                catch (Exception ex) {
                    String warning = "Dataset " + datasetSpec + " could not be fetched: " + Objects.toString(ex.getMessage(), "Cloud API error");
                    warnings.add(warning); log.warn(warning, ex); continue;
                }
                if (config.isPushReportsEnabled() && isReportDataset(dataset)) {
                    try {
                        PushSummary pushSummary = pushReportChanges(client, config, cloudInstanceId, dataset, category, exported.records(), runId);
                        pushed += pushSummary.rows();
                        legacyIdConflicts += pushSummary.remappedLegacyIds();
                        exported = export(client, config, dataset, category);
                    } catch (Exception ex) {
                        String warning = "Dataset " + datasetSpec + " push failed; Cloud records were still fetched: " + Objects.toString(ex.getMessage(), "Cloud API error");
                        warnings.add(warning); log.warn(warning, ex);
                    }
                }
                exported.warnings().forEach(w -> warnings.add(datasetSpec + ": " + w));
                snapshots.add(exported);
                downloaded++;
            }
            if (snapshots.isEmpty()) throw new IOException("Cloud API returned no usable datasets; no records were synchronized");
            Map<String, Object> result = dataSync.applyJsonSnapshots(snapshots, cloudInstanceId, config.isPushReportsEnabled());
            int created = ((Number) result.getOrDefault("created", 0)).intValue();
            int updated = ((Number) result.getOrDefault("updated", 0)).intValue();
            int unchanged = ((Number) result.getOrDefault("unchanged", 0)).intValue();
            int deleted = ((Number) result.getOrDefault("deleted", 0)).intValue();
            if (result.get("warnings") instanceof Collection<?> items) items.forEach(item -> warnings.add(String.valueOf(item)));
            List<DataSyncService.JsonSyncExport> appliedSnapshots = result.get("successfulSnapshots") instanceof Collection<?> applied
                    ? applied.stream().filter(DataSyncService.JsonSyncExport.class::isInstance)
                    .map(DataSyncService.JsonSyncExport.class::cast).toList() : List.of();
            if (appliedSnapshots.isEmpty()) throw new IOException("No datasets could be applied; " + warnings.stream().limit(3).collect(java.util.stream.Collectors.joining("; ")));
            // First commit data. Attachment failures are independent and never roll back report changes.
            for (var snapshot : appliedSnapshots)
                attachmentFailures += downloadReferencedAttachments(client, config, snapshot.dataset(), snapshot.records(), warnings);
            config.setLastStatus("SUCCESS");
            config.setLastSuccessAt(LocalDateTime.now());
            String message = warnings.isEmpty()
                    ? "Sync finished successfully. Added " + created + " items, updated " + updated + ", and removed " + deleted + "."
                    : attachmentFailures > 0
                    ? "Sync finished successfully. Some files or records need attention; the remaining data was synced."
                    : "Sync finished successfully. Some records need attention; the remaining data was synced.";
            config.setLastMessage(message.substring(0, Math.min(message.length(), 2000)));
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("Cloud sync run {} failed: {}", config.getLastRunAt(), ex.getMessage(), ex);
            config.setLastStatus("ERROR");
            config.setLastMessage("Sync could not be completed. Check the connection and try again.");
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
                    .filter(p -> p.toFile().lastModified() <= cutoff).toList(), List.of(), UUID.randomUUID().toString(), null, null);
        }
    }

    private String processFiles(SyncConfiguration config, List<Path> batch, List<String> sourceWarnings, String runId,
                                HttpClient attachmentClient, String cloudInstanceId) {
        Path processing = Path.of(config.getProcessingFolder());
        Path failed = Path.of(config.getFailedFolder());
        Set<String> warnings = new LinkedHashSet<>(sourceWarnings);
        try { Files.createDirectories(processing); Files.createDirectories(failed); }
        catch (IOException ex) {
            warnings.add("CSV staging folders are unavailable; direct API sync can continue: " + ex.getMessage());
            return warningImportSummary(warnings);
        }
        List<ImportedSnapshot> importedSnapshots = new ArrayList<>();
        int created = 0, updated = 0, unchanged = 0, imported = 0;
        List<Path> ordered = batch.stream().sorted(Comparator.comparingInt(CloudSyncService::importPriority)
                .thenComparing(path -> path.getFileName().toString())).toList();
        for (Path file : ordered) {
            Path staged = null;
            try {
                if (!Files.isRegularFile(file)) throw new IOException("File is no longer available");
                staged = Files.move(file, processing.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                String[] parts = parseDatasetAndCategory(staged.getFileName().toString());
                String dataset = parts[0], category = parts.length > 1 ? parts[1] : "";
                String csv = Files.readString(staged);
                Map<String, Object> result;
                if (dataset.equals("users")) { dataSync.replaceUsersForCloudSync(csv); result = Map.of(); }
                else result = config.isPushReportsEnabled() && cloudInstanceId != null && isReportDataset(dataset)
                        ? dataSync.importCsvFromCloud(cloudInstanceId, dataset, category, csv)
                        : dataSync.importCsvForSync(dataset, category, csv);
                created += ((Number) result.getOrDefault("created", 0)).intValue();
                updated += ((Number) result.getOrDefault("updated", 0)).intValue();
                unchanged += ((Number) result.getOrDefault("unchanged", 0)).intValue();
                if (result.get("warnings") instanceof Collection<?> items) items.forEach(item -> warnings.add(dataset + ": " + item));
                importedSnapshots.add(new ImportedSnapshot(dataset, category, csv)); imported++;
                downloadReferencedAttachments(attachmentClient, config, dataset, csv, warnings);
            } catch (Exception ex) {
                String warning = file.getFileName() + " could not be processed; later files will continue: "
                        + Objects.toString(ex.getMessage(), "import failed");
                warnings.add(warning); log.warn(warning, ex);
                if (staged != null && Files.exists(staged)) try {
                    Files.move(staged, failed.resolve(staged.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveError) { log.warn("Unable to move failed sync file {}", staged, moveError); }
            } finally {
                if (staged != null) try { Files.deleteIfExists(staged); }
                catch (IOException ex) { log.warn("Unable to clean staged sync file {}", staged, ex); }
            }
        }
        for (ImportedSnapshot snapshot : importedSnapshots.stream()
                .sorted(Comparator.comparingInt((ImportedSnapshot item) -> SyncDatasetPlan.priority(item.dataset(), item.category())).reversed()).toList()) {
            if (snapshot.dataset().equals("users") || config.isPushReportsEnabled() && cloudInstanceId != null && isReportDataset(snapshot.dataset())) continue;
            try { dataSync.deleteMissingForSync(snapshot.dataset(), snapshot.category(), snapshot.csv()); }
            catch (Exception ex) { String warning = "Deletion reconciliation for " + snapshot.dataset() + " was skipped: " + ex.getMessage(); warnings.add(warning); log.warn(warning, ex); }
        }
        if (config.isPushReportsEnabled() && cloudInstanceId != null) for (ImportedSnapshot snapshot : importedSnapshots) if (isReportDataset(snapshot.dataset()))
            try { dataSync.updatePeerHashes(cloudInstanceId, snapshot.dataset(), snapshot.category()); }
            catch (Exception ex) { String warning = "Peer tracking for " + snapshot.dataset() + " was not updated: " + ex.getMessage(); warnings.add(warning); log.warn(warning, ex); }
        String outcome = imported == 0 && !batch.isEmpty() ? "CSV sync did not import any files; " : "CSV sync processed " + imported + " file(s); ";
        return outcome + created + " added, " + updated + " replaced, " + unchanged
                + " unchanged. " + warningImportSummary(warnings);
    }

    private static String warningImportSummary(Set<String> warnings) {
        return warnings.size() + " warning(s). " + warnings.stream().limit(3).collect(java.util.stream.Collectors.joining("; "));
    }

    private int downloadReferencedAttachments(HttpClient client, SyncConfiguration config, String dataset, ArrayNode records,
            Set<String> warnings) {
        if (attachmentStorage == null || client == null) return 0; // Folder-only processing has no authenticated cloud session.
        String module = attachmentModule(dataset);
        if (module.isBlank()) return 0;
        Set<String> filenames;
        try { filenames = referencedAttachmentNames(records); }
        catch (IOException ex) { warnings.add(dataset + " attachments: " + ex.getMessage()); return 1; }
        int failures = 0;
        for (String filename : filenames) {
            String endpoint = url(config) + "/api/cloud-sync/attachments/" + module + "/"
                    + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            try {
                String checksumEndpoint = endpoint + "/metadata";
                HttpResponse<String> metadata = sendAuthenticated(client, config, HttpRequest.newBuilder(URI.create(checksumEndpoint))
                        .timeout(java.time.Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (metadata.statusCode() == 404) {
                    String warning = "Cloud attachment " + module + "/" + filename + " is missing (HTTP 404)";
                    warnings.add(warning); log.warn(warning); failures++; continue;
                }
                if (metadata.statusCode() / 100 != 2) throw cloudError("read attachment metadata " + filename, metadata.statusCode(), metadata.body());
                JsonNode version = mapper.readTree(metadata.body());
                if (version.path("missing").asBoolean(false)) {
                    String warning = "Cloud attachment " + module + "/" + filename + " is missing";
                    warnings.add(warning); log.warn(warning); failures++; continue;
                }
                String checksum = version.path("checksum").asText("");
                long size = version.path("size").asLong(-1);
                if (!checksum.matches("(?i)[a-f0-9]{64}") || size <= 0 || size > 10 * 1024 * 1024)
                    throw new IOException("Cloud returned invalid attachment metadata for " + filename);
                // Opaque filenames are immutable: when present locally, compare bytes and avoid the download.
                if (attachmentStorage.matchesImage(module, filename, checksum)) continue;
                HttpResponse<byte[]> response = sendAuthenticated(client, config, HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(java.time.Duration.ofMinutes(2)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 404) {
                    String warning = "Cloud attachment " + module + "/" + filename + " disappeared during download (HTTP 404)";
                    warnings.add(warning); log.warn(warning); failures++; continue;
                }
                if (response.statusCode() / 100 != 2) throw cloudError("download attachment " + filename, response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
                byte[] bytes = response.body();
                if (bytes.length != size) throw new IOException("Cloud attachment size changed during download: " + filename);
                String downloadedChecksum = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
                if (!downloadedChecksum.equalsIgnoreCase(checksum)) throw new IOException("Cloud attachment checksum changed during download: " + filename);
                attachmentStorage.storeSyncedImage(module, filename, bytes);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                String warning = "Interrupted while downloading cloud attachment " + module + "/" + filename;
                warnings.add(warning); log.warn(warning, ex); failures++; break;
            } catch (Exception ex) {
                String warning = "Cloud attachment " + module + "/" + filename + " could not be synced: " + Objects.toString(ex.getMessage(), "attachment transfer failed");
                warnings.add(warning); log.warn(warning, ex); failures++;
            }
        }
        return failures;
    }

    private void downloadReferencedAttachments(HttpClient client, SyncConfiguration config, String dataset, String csv) {
        downloadReferencedAttachments(client, config, dataset, csv, new LinkedHashSet<>());
    }

    private void downloadReferencedAttachments(HttpClient client, SyncConfiguration config, String dataset, String csv,
            Set<String> warnings) {
        if (client == null || attachmentStorage == null) return;
        String module = attachmentModule(dataset);
        if (module.isBlank()) return;
        Set<String> filenames;
        try { filenames = referencedAttachmentNames(csv); }
        catch (IOException ex) { warnings.add(dataset + " attachment references could not be read: " + ex.getMessage()); return; }
        for (String filename : filenames) {
            if (attachmentStorage.hasImage(module, filename)) continue;
            String endpoint = url(config) + "/api/cloud-sync/attachments/" + module + "/"
                    + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            try {
                HttpResponse<byte[]> response = sendAuthenticated(client, config, HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(java.time.Duration.ofMinutes(2)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 404) { warnings.add("Cloud attachment " + module + "/" + filename + " is missing (HTTP 404)"); continue; }
                if (response.statusCode() / 100 != 2) throw cloudError("download attachment " + filename, response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
                if (!attachmentStorage.matchesImage(module, filename, java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(response.body()))))
                    attachmentStorage.storeSyncedImage(module, filename, response.body());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); warnings.add("Interrupted while downloading cloud attachment " + module + "/" + filename); return;
            } catch (Exception ex) {
                warnings.add("Cloud attachment " + module + "/" + filename + " could not be synced: " + Objects.toString(ex.getMessage(), "attachment transfer failed"));
                log.warn("Cloud attachment {}/{} could not be synced; continuing", module, filename, ex);
            }
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
        HttpResponse<String> response = sendWithRetry(client, HttpRequest.newBuilder(URI.create(url(c) + "/api/auth/login"))
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

    private DataSyncService.JsonSyncExport export(HttpClient client, SyncConfiguration c, String dataset, String category) throws IOException, InterruptedException {
        HttpResponse<String> response = sendAuthenticated(client, c, HttpRequest.newBuilder(URI.create(url(c) + "/api/cloud-sync/data/" + dataset + "?category=" +
                java.net.URLEncoder.encode(category, StandardCharsets.UTF_8)))
                .timeout(java.time.Duration.ofMinutes(5)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String operation = "export " + dataset + (category.isBlank() ? "" : ":" + category);
        if (response.statusCode() / 100 != 2)
            throw cloudError(operation, response.statusCode(), response.body());
        try {
            var exported = mapper.readValue(response.body(), DataSyncService.JsonSyncExport.class);
            if (exported.version() != 1 || exported.records() == null || !dataset.equals(exported.dataset()) || !category.equals(exported.category()) || exported.warnings() == null)
                throw new IOException("Invalid sync export format");
            return exported;
        } catch (IOException ex) {
            throw new IOException("Cloud " + operation + " returned an invalid sync export. Deploy the same updated build on both servers.", ex);
        }
    }

    private String cloudIdentity(HttpClient client, SyncConfiguration c) throws IOException, InterruptedException {
        HttpResponse<String> response = sendAuthenticated(client, c, HttpRequest.newBuilder(URI.create(url(c) + "/api/cloud-sync/identity"))
                .timeout(java.time.Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) throw cloudError("read cloud identity", response.statusCode(), response.body());
        try { return mapper.readTree(response.body()).path("instanceId").asText(""); }
        catch (IOException ex) { throw new IOException("Cloud identity endpoint returned invalid JSON", ex); }
    }

    private record PushSummary(int rows, int remappedLegacyIds) { }

    private PushSummary pushReportChanges(HttpClient client, SyncConfiguration config, String cloudInstanceId,
                                   String dataset, String category, ArrayNode cloudRecords, String runId)
            throws IOException, InterruptedException {
        DataSyncService.JsonSyncExport local = dataSync.exportJsonForSync(dataset, category);
        String datasetKey = dataset + (category.isBlank() ? "" : ":" + category);
        List<PeerRow> cloudRows = jsonRows(cloudRecords);
        Map<Long, String> cloudHashes = new HashMap<>();
        cloudRows.forEach(row -> cloudHashes.put(row.id(), row.hash()));
        List<PeerRow> changed = new ArrayList<>();
        Map<Long, Long> knownIds = new LinkedHashMap<>();
        int remappedLegacyIds = 0;
        for (PeerRow row : jsonRows(local.records())) {
            SyncRecordMapping mapping = syncMappings.findByPeerInstanceIdAndDatasetKeyAndLocalRecordId(
                    cloudInstanceId, datasetKey, row.id()).orElse(null);
            if (mapping == null && cloudHashes.containsKey(row.id())) {
                if (Objects.equals(cloudHashes.get(row.id()), row.hash())) {
                    mapping = new SyncRecordMapping();
                    mapping.setPeerInstanceId(cloudInstanceId);
                    mapping.setDatasetKey(datasetKey);
                    mapping.setPeerRecordId(row.id());
                    mapping.setLocalRecordId(row.id());
                    mapping.setLocalHash(cloudHashes.get(row.id()));
                    mapping = syncMappings.save(mapping);
                } else {
                    // Old installations have no identity map. Treat a same-ID/different-content
                    // row as a separate On-Prem record so bootstrap cannot overwrite Cloud data.
                    log.warn("Legacy sync ID {} in {} differs between installations; publishing it as a new Cloud record", row.id(), datasetKey);
                    remappedLegacyIds++;
                }
            }
            if (mapping != null) {
                knownIds.put(row.id(), mapping.getPeerRecordId());
                if (Objects.equals(mapping.getLocalHash(), row.hash())) continue;
            }
            changed.add(row);
        }
        if (changed.isEmpty()) return new PushSummary(0, remappedLegacyIds);
        Set<Long> changedIds = changed.stream().map(PeerRow::id).collect(java.util.stream.Collectors.toSet());
        ArrayNode records = mapper.createArrayNode();
        local.records().forEach(row -> { if (changedIds.contains(row.path("id").asLong())) records.add(row); });
        uploadReferencedAttachments(client, config, dataset, records);
        int batchNumber = 0;
        for (ArrayNode batch : pushBatches(records)) {
            Set<Long> batchIds = new HashSet<>();
            batch.forEach(row -> batchIds.add(row.path("id").asLong()));
            List<PeerRow> batchRows = changed.stream().filter(row -> batchIds.contains(row.id())).toList();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("peerInstanceId", config.getInstanceId());
            payload.put("dataset", dataset);
            payload.put("category", category);
            payload.put("records", batch);
            Map<String, Long> idHints = new LinkedHashMap<>();
            batchRows.forEach(row -> { if (knownIds.containsKey(row.id())) idHints.put(String.valueOf(row.id()), knownIds.get(row.id())); });
            payload.put("knownIds", idHints);
            String body = mapper.writeValueAsString(payload);
            String key = config.getInstanceId() + ":" + runId + ":" + datasetKey + ":" + batchNumber++;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url(config) + "/api/cloud-sync/push"))
                    .timeout(java.time.Duration.ofMinutes(5)).header("Content-Type", "application/json")
                    .header("Idempotency-Key", key).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = sendAuthenticated(client, config, request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) throw cloudError("push " + datasetKey, response.statusCode(), response.body());
            JsonNode mappings;
            try { mappings = mapper.readTree(response.body()).path("idMappings"); }
            catch (IOException ex) { throw new IOException("Cloud push returned invalid JSON", ex); }
            for (PeerRow row : batchRows) {
                JsonNode cloudId = mappings.path(String.valueOf(row.id()));
                if (!cloudId.canConvertToLong()) throw new IOException("Cloud push did not confirm record " + row.id());
                long remoteId = cloudId.asLong();
                SyncRecordMapping mapping = syncMappings.findByPeerInstanceIdAndDatasetKeyAndLocalRecordId(
                        cloudInstanceId, datasetKey, row.id()).orElseGet(SyncRecordMapping::new);
                mapping.setPeerInstanceId(cloudInstanceId); mapping.setDatasetKey(datasetKey);
                mapping.setPeerRecordId(remoteId); mapping.setLocalRecordId(row.id()); mapping.setLocalHash(row.hash());
                syncMappings.save(mapping);
            }
        }
        return new PushSummary(changed.size(), remappedLegacyIds);
    }

    List<ArrayNode> pushBatches(ArrayNode records) throws IOException {
        List<ArrayNode> batches = new ArrayList<>();
        ArrayNode batch = mapper.createArrayNode();
        int bytes = 2;
        for (JsonNode row : records) {
            int size = mapper.writeValueAsBytes(row).length + 1;
            if (size > 19 * 1024 * 1024) throw new IOException("Report " + row.path("id").asText() + " exceeds the Cloud push size limit");
            if (!batch.isEmpty() && (batch.size() >= 5000 || bytes + size > 19 * 1024 * 1024)) {
                batches.add(batch); batch = mapper.createArrayNode(); bytes = 2;
            }
            batch.add(row); bytes += size;
        }
        if (!batch.isEmpty()) batches.add(batch);
        return batches;
    }

    private List<PeerRow> jsonRows(ArrayNode records) throws IOException {
        List<PeerRow> result = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (JsonNode row : records) {
            if (!(row instanceof ObjectNode object) || !row.path("id").isIntegralNumber()
                    || row.path("id").asLong() <= 0 || !ids.add(row.path("id").asLong()))
                throw new IOException("JSON sync response contains an invalid or duplicate record ID");
            result.add(new PeerRow(row.path("id").asLong(), dataSync.hashJsonRecord(object)));
        }
        return result;
    }

    static Set<String> referencedAttachmentNames(JsonNode records) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        collectAttachments(records, names, false);
        return names;
    }

    private static void collectAttachments(JsonNode node, Set<String> names, boolean includeExisting) throws IOException {
        if (node == null || node.isNull()) return;
        if (node.isArray()) { for (JsonNode child : node) collectAttachments(child, names, includeExisting); }
        else if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (isAttachmentColumn(field.getKey()) && !field.getValue().asText("").isBlank()
                        && (includeExisting || !node.path("_" + field.getKey() + "Unchanged").asBoolean(false))) names.add(field.getValue().asText().trim());
                else if (isAttachmentJsonColumn(field.getKey()) && field.getValue().isTextual()) {
                    String json = field.getValue().asText();
                    if (!json.isBlank()) collectAttachments(new ObjectMapper().readTree(json), names, includeExisting);
                } else if (field.getValue().isContainerNode()) collectAttachments(field.getValue(), names, includeExisting);
            }
        }
    }

    private record PeerRow(long id, String hash) { }

    private List<PeerRow> parseRows(String csv) throws IOException {
        if (csv.startsWith("\ufeff")) csv = csv.substring(1);
        List<PeerRow> rows = new ArrayList<>();
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(new StringReader(csv))) {
            if (!parser.getHeaderNames().contains("id")) throw new IOException("Sync CSV has no id column");
            for (CSVRecord row : parser) {
                if (!row.isConsistent()) throw new IOException("Sync CSV contains an inconsistent row");
                long id;
                try { id = Long.parseLong(row.get("id")); }
                catch (RuntimeException ex) { throw new IOException("Sync CSV has an invalid report ID", ex); }
                StringBuilder canonical = new StringBuilder();
                for (String header : parser.getHeaderNames()) {
                    if (header.equals("id") || isReferenceIdColumn(header)) continue;
                    String cell = row.get(header);
                    if (isNestedObservationColumn(header) && !cell.isBlank()) cell = stripNestedIds(cell);
                    canonical.append(header.length()).append(':').append(header).append(cell.length()).append(':').append(cell).append('\n');
                }
                rows.add(new PeerRow(id, sha256(canonical.toString())));
            }
        }
        return rows;
    }

    private String csvForRows(String csv, Set<Long> ids) throws IOException {
        if (csv.startsWith("\ufeff")) csv = csv.substring(1);
        StringWriter output = new StringWriter();
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(new StringReader(csv));
             org.apache.commons.csv.CSVPrinter printer = new org.apache.commons.csv.CSVPrinter(output,
                     CSVFormat.DEFAULT.builder().setHeader(parser.getHeaderNames().toArray(String[]::new)).build())) {
            for (CSVRecord row : parser) if (ids.contains(Long.parseLong(row.get("id")))) printer.printRecord(row);
        }
        return output.toString();
    }

    private void uploadReferencedAttachments(HttpClient client, SyncConfiguration config, String dataset, ArrayNode records)
            throws IOException, InterruptedException {
        String module = attachmentModule(dataset);
        if (module.isBlank() || attachmentStorage == null) return;
        for (String filename : referencedAttachmentNames(records)) {
            byte[] bytes = attachmentStorage.readImage(module, filename);
            if (bytes.length > 10 * 1024 * 1024) throw new IOException("Attachment exceeds 10 MB: " + filename);
            String type = filename.toLowerCase(Locale.ROOT).endsWith(".png") ? "image/png" : "image/jpeg";
            String path = "/api/cloud-sync/attachments/" + module + "/" + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            HttpRequest request = HttpRequest.newBuilder(URI.create(url(config) + path)).timeout(java.time.Duration.ofMinutes(2))
                    .header("Content-Type", "application/octet-stream").PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
            HttpResponse<String> response = sendAuthenticated(client, config, request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw cloudError("upload attachment " + filename, response.statusCode(), response.body());
        }
    }

    private <T> HttpResponse<T> sendWithRetry(HttpClient client, HttpRequest request,
            HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpResponse<T> response = client.send(request, handler);
                if ((response.statusCode() < 500 && response.statusCode() != 408 && response.statusCode() != 429) || attempt == 2) return response;
                last = new IOException("Cloud temporarily returned HTTP " + response.statusCode());
            } catch (IOException ex) {
                last = ex;
                if (attempt == 2) throw ex;
            }
            Thread.sleep(500L << attempt);
        }
        throw last == null ? new IOException("Cloud request failed") : last;
    }

    private <T> HttpResponse<T> sendAuthenticated(HttpClient client, SyncConfiguration config, HttpRequest request,
            HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
        HttpResponse<T> response = sendWithRetry(client, request, handler);
        if (response.statusCode() == 401) {
            login(client, config);
            response = sendWithRetry(client, request, handler);
        }
        return response;
    }

    private String stripNestedIds(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        stripIds(root);
        return root.toString();
    }

    private static void stripIds(JsonNode node) {
        if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
            object.remove("id"); object.elements().forEachRemaining(CloudSyncService::stripIds);
        } else if (node != null && node.isArray()) node.elements().forEachRemaining(CloudSyncService::stripIds);
    }

    private static String sha256(String text) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static boolean isNestedObservationColumn(String column) {
        return column.equals("observations") || column.equals("zmObservationsJson")
                || column.equals("pmObservationsJson") || column.equals("qmObservationsJson");
    }

    private static boolean isReferenceIdColumn(String column) {
        return Set.of("plantId", "departmentId", "departmentIds", "processAreaId", "areaIds", "designationId",
                "parentPlantId", "parentDepartmentId", "classificationOfKaizenId", "typeOfTagId",
                "abnormalityDefectTypeId", "gembaCategoryId", "lifeSaverRuleId").contains(column);
    }

    private static boolean isReportDataset(String dataset) {
        return Set.of("gemba-walk", "gemba-kaizen", "abnormality", "process-confirmation").contains(dataset);
    }

    private static String attachmentModule(String dataset) {
        return switch (dataset) {
            case "abnormality" -> "abnormality-reporting";
            case "gemba-walk", "gemba-kaizen", "process-confirmation" -> dataset;
            default -> "";
        };
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
                && !c.getEncryptedCloudPassword().isBlank() && !c.getDatasets().isBlank();
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
        boolean httpOrHttps = "https".equalsIgnoreCase(source.getScheme())
                || "http".equalsIgnoreCase(source.getScheme());
        if (!httpOrHttps
                || source.getHost() == null || source.getUserInfo() != null || source.getQuery() != null || source.getFragment() != null)
            throw new IllegalArgumentException("Cloud PMS URL must use HTTP or HTTPS and must not contain credentials, query parameters or a fragment");
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
