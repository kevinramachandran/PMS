package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.csv.*;
import org.example.entity.*;
import org.example.util.RoleAccess;
import org.example.entity.SyncRecordMapping;
import org.example.repository.SyncRecordMappingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.*;
import java.util.*;
import java.util.function.*;
import java.lang.reflect.Proxy;

/** CSV adapters deliberately call the normal services: permissions, validation and history still apply. */
@Service
public class DataSyncService {
    private final ObjectMapper mapper;
    private final GembaKaizenConfigService kaizen;
    private final AbnormalityReportingConfigService abnormality;
    private final GembaWalkConfigService walk;
    private final CarlexProcessConfirmationService process;
    private final PlantMasterDataService plants;
    private final GembaKaizenMasterDataService kaizenMasters;
    private final AbnormalityMasterDataService abnormalityMasters;
    private final GembaWalkMasterDataService walkMasters;
    private final ProcessMasterDataService processMasters;
    private final AuthService users;
    private final MasterReferenceService references;
    @org.springframework.beans.factory.annotation.Autowired
    private CloudSyncRecordWriter cloudWriter;
    @org.springframework.beans.factory.annotation.Autowired
    private SyncRecordMappingRepository syncMappings;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    public DataSyncService(ObjectMapper mapper, GembaKaizenConfigService kaizen,
            AbnormalityReportingConfigService abnormality, GembaWalkConfigService walk,
            CarlexProcessConfirmationService process, PlantMasterDataService plants,
            GembaKaizenMasterDataService kaizenMasters, AbnormalityMasterDataService abnormalityMasters,
            GembaWalkMasterDataService walkMasters, ProcessMasterDataService processMasters, AuthService users,
            MasterReferenceService references) {
        this.mapper = mapper; this.kaizen = kaizen; this.abnormality = abnormality; this.walk = walk;
        this.process = process; this.plants = plants; this.kaizenMasters = kaizenMasters;
        this.abnormalityMasters = abnormalityMasters; this.walkMasters = walkMasters;
        this.processMasters = processMasters; this.users = users;
        this.references = references;
    }

    private record Dataset(Class<?> type, String page, Supplier<? extends List<?>> rows,
                           BiFunction<Long, ObjectNode, Object> save, Set<String> excluded) {}

    private <T> Dataset records(Class<T> type, String page, Supplier<List<T>> list,
            Function<T, T> create, BiFunction<Long, T, Optional<T>> update, String... excluded) {
        return new Dataset(type, page, list, (id, node) -> {
            T value = mapper.convertValue(references.payload(type, node), type);
            if (id == null) return create.apply(value);
            return update.apply(id, value).orElseThrow(() -> new IllegalArgumentException("Record unavailable or not editable"));
        }, Set.of(excluded));
    }

    private Dataset master(Class<?> type, String page, Supplier<? extends List<?>> list,
            Function<String, ?> add, BiFunction<Long, String, ? extends Optional<?>> update) {
        return new Dataset(type, page, list, (id, node) -> {
            if (id == null) return add.apply(value(node, "name"));
            return update.apply(id, value(node, "name")).orElseThrow(() -> new IllegalArgumentException("Item not found"));
        }, Set.of("category"));
    }

    private Dataset dataset(String key, String category, HttpSession session, boolean edit) {
        String username = session == null ? "" : Objects.toString(session.getAttribute("username"), "");
        String role = session == null ? "" : Objects.toString(session.getAttribute("role"), "");
        Dataset data = switch (key) {
            case "gemba-kaizen" -> records(GembaKaizenRecord.class, RoleAccess.PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION,
                    () -> kaizen.listForUser(username, role), v -> kaizen.create(v, username, role),
                    (id, v) -> kaizen.update(id, v, username, role));
            case "abnormality" -> records(AbnormalityReportingRecord.class, RoleAccess.PAGE_ABNORMALITY_TRACKER_CONFIGURATION,
                    () -> abnormality.listForUser(username, role), v -> abnormality.create(v, username, role),
                    (id, v) -> abnormality.update(id, v, username, role));
            case "gemba-walk" -> records(GembaWalkRecord.class, RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION,
                    () -> walk.listForUser(username, role), v -> walk.create(v, username, role),
                    (id, v) -> walk.update(id, v, username, role), "displayManagerName", "displayEmail", "createdAt", "updatedAt");
            case "process-confirmation" -> records(CarlexProcessConfirmation.class, RoleAccess.PAGE_PROCESS_CONFIRMATION_CONFIGURATION,
                    () -> process.listForUser(username, role), v -> process.create(v, username, role),
                    (id, v) -> process.update(id, v, username, role), "observations");
            case "plant-master" -> new Dataset(PlantMasterDataItem.class, RoleAccess.PAGE_KPI_PLANT_NAME,
                    () -> plants.list(category), (id, n) -> {
                        n = references.currentMasterLabels(PlantMasterDataItem.class, n);
                        if (id == null) return plants.add(category, value(n, "name"), value(n, "parentPlant"), value(n, "parentDepartment"), value(n, "parentProcessArea"));
                        return plants.update(id, value(n, "name"), value(n, "parentPlant"), value(n, "parentDepartment"), value(n, "parentProcessArea"))
                                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
                    }, Set.of("category"));
            case "kaizen-master" -> master(GembaKaizenMasterDataItem.class, RoleAccess.PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION,
                    () -> kaizenMasters.list(category), name -> kaizenMasters.add(category, name), kaizenMasters::update);
            case "abnormality-master" -> master(AbnormalityMasterDataItem.class, RoleAccess.PAGE_ABNORMALITY_TRACKER_CONFIGURATION,
                    () -> abnormalityMasters.list(category), name -> abnormalityMasters.add(category, name), abnormalityMasters::update);
            case "walk-master" -> master(GembaWalkMasterDataItem.class, RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION,
                    () -> walkMasters.list(category), name -> walkMasters.add(category, name), walkMasters::update);
            case "process-master" -> master(ProcessMasterDataItem.class, RoleAccess.PAGE_PROCESS_CONFIRMATION_CONFIGURATION,
                    () -> processMasters.list(category), name -> processMasters.add(category, name), processMasters::update);
            case "users" -> new Dataset(AppUser.class, RoleAccess.PAGE_USER_MANAGEMENT, users::getManageableUsers,
                    this::saveUser, Set.of());
            default -> throw new IllegalArgumentException("Unsupported data sync page");
        };
        Object raw = session == null ? null : session.getAttribute(edit ? "editPermissions" : "viewPermissions");
        Set<String> permissions = new HashSet<>();
        if (raw instanceof Collection<?> values) values.forEach(v -> permissions.add(String.valueOf(v)));
        boolean allowed = edit ? RoleAccess.canEditPage(role, permissions, data.page()) : RoleAccess.canViewPage(role, permissions, data.page());
        if (username.isBlank() || !allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        return data;
    }

    private List<String> columns(Dataset data) {
        List<String> columns;
        if (data.type() == AppUser.class) columns = new ArrayList<>(List.of("id", "username", "name", "employeeId", "department", "area", "plant",
                "designation", "reportingManager", "email", "role", "status", "pageViewPermissions", "pageEditPermissions", "password"));
        else columns = new ArrayList<>(mapper.getSerializationConfig().introspect(mapper.constructType(data.type())).findProperties().stream()
                .map(p -> p.getName()).filter(n -> !data.excluded().contains(n) && !n.equals("masterReferences")).toList());
        references.links(data.type()).forEach(link -> columns.add(link.column()));
        return columns;
    }

    private ObjectNode row(Dataset data, Object value) {
        ObjectNode node = mapper.valueToTree(value);
        data.excluded().forEach(node::remove);
        if (data.type() == AppUser.class) node.put("password", "");
        return node;
    }

    @Transactional
    public String exportCsv(String key, String category, HttpSession session) throws IOException {
        return exportRows(key, category, session, false).csv();
    }

    public record CsvExport(int version, String csv, List<String> warnings) { }

    public record JsonSyncExport(int version, String dataset, String category, ArrayNode records, List<String> warnings) { }

    /** Structured sync snapshot. The wire representation contains records, not CSV text or files. */
    @Transactional
    public JsonSyncExport exportJsonForSync(String key, String category) {
        HttpSession session = syncSession();
        Dataset data = dataset(key, category, session, false);
        var catalog = references.catalog();
        List<String> warnings = new ArrayList<>();
        ArrayNode records = mapper.createArrayNode();
        List<String> allowed = columns(data);
        if (data.type() == GembaWalkRecord.class) allowed.addAll(List.of("createdAt", "updatedAt"));
        for (Object item : data.rows().get()) {
            ObjectNode source = row(data, item);
            if (item instanceof GembaWalkRecord walk) {
                source.set("createdAt", mapper.valueToTree(walk.getCreatedAt()));
                source.set("updatedAt", mapper.valueToTree(walk.getUpdatedAt()));
            }
            if (item instanceof AppUser user) source.put("password", users.passwordHashForSync(user));
            references.enrich(item, source, catalog, warnings);
            ObjectNode record = mapper.createObjectNode();
            for (String field : allowed) if (source.has(field)) record.set(field, source.get(field).deepCopy());
            if (historyModule(key) != null) record.set("assignmentHistory", cloudWriter.assignmentHistory(historyModule(key), record.path("id").asLong()));
            records.add(record);
        }
        return new JsonSyncExport(1, key, Objects.toString(category, ""), records, warnings.stream().distinct().toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importJsonForSync(String key, String category, ArrayNode records) {
        return importJson(key, category, records, syncSession(), true);
    }

    /** Replace cloud-owned local users only after validating the complete JSON snapshot. */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> replaceUsersForCloudSync(ArrayNode records) {
        if (records == null || records.isEmpty()) throw new IllegalArgumentException("Cloud user snapshot is empty");
        Set<String> usernames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER), emails = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (JsonNode row : records) {
            String username = row.path("username").asText("").trim(), email = row.path("email").asText("").trim();
            if (username.isBlank() || email.isBlank() || !usernames.add(username) || !emails.add(email))
                throw new IllegalArgumentException("Cloud user snapshot has blank or duplicate usernames/emails");
            if (!row.path("password").asText("").matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}"))
                throw new IllegalArgumentException("Cloud user snapshot is missing a valid password hash for " + username
                        + ". Update Cloud before syncing; local accounts have not been replaced.");
        }
        cloudWriter.deleteAllUsersForCloudReplacement();
        return importJson("users", "", records, syncSession(), true);
    }

    static String historyModule(String dataset) {
        return switch (dataset) {
            case "abnormality" -> "abnormality-reporting";
            case "process-confirmation" -> "carlex-process-confirmation";
            case "gemba-kaizen", "gemba-walk" -> dataset;
            default -> null;
        };
    }

    /** Each dataset commits independently so one bad snapshot cannot block the remaining modules. */
    public Map<String, Object> applyJsonSnapshots(List<JsonSyncExport> snapshots, String cloudInstanceId, boolean bidirectional) throws IOException {
        int created = 0, updated = 0, unchanged = 0, deleted = 0;
        List<String> warnings = new ArrayList<>();
        List<JsonSyncExport> successfulSnapshots = new ArrayList<>();
        for (JsonSyncExport snapshot : snapshots) {
            try {
                Map<String, Object> result = runInIndependentDatasetTransaction(() -> {
                    Map<String, Object> imported = snapshot.dataset().equals("users") ? replaceUsersForCloudSync(snapshot.records())
                            : bidirectional && historyModule(snapshot.dataset()) != null
                            ? importJsonFromCloud(cloudInstanceId, snapshot.dataset(), snapshot.category(), snapshot.records())
                            : importJsonForSync(snapshot.dataset(), snapshot.category(), snapshot.records());
                    int removed = snapshot.dataset().equals("users") ? 0 : deleteMissingForSync(snapshot.dataset(),
                            snapshot.category(), snapshot.records(), bidirectional && historyModule(snapshot.dataset()) != null ? cloudInstanceId : null);
                    Map<String, Object> outcome = new LinkedHashMap<>(imported);
                    outcome.put("deleted", removed);
                    return outcome;
                });
                created += ((Number) result.getOrDefault("created", 0)).intValue();
                updated += ((Number) result.getOrDefault("updated", 0)).intValue();
                unchanged += ((Number) result.getOrDefault("unchanged", 0)).intValue();
                deleted += ((Number) result.getOrDefault("deleted", 0)).intValue();
                if (result.get("warnings") instanceof Collection<?> items) items.forEach(w -> warnings.add(snapshot.dataset() + ": " + w));
                if (bidirectional && historyModule(snapshot.dataset()) != null)
                    updatePeerHashes(cloudInstanceId, snapshot.dataset(), snapshot.category());
                successfulSnapshots.add(snapshot);
            } catch (Exception ex) {
                String warning = "Dataset " + snapshot.dataset() + (snapshot.category().isBlank() ? "" : ":" + snapshot.category())
                        + " could not be applied; remaining datasets will continue: " + Objects.toString(ex.getMessage(), "database import failed");
                warnings.add(warning);
                org.slf4j.LoggerFactory.getLogger(DataSyncService.class).warn(warning, ex);
            }
        }
        return Map.of("created", created, "updated", updated, "unchanged", unchanged, "deleted", deleted, "warnings", warnings,
                "successfulSnapshots", successfulSnapshots);
    }

    private <T> T runInIndependentDatasetTransaction(Supplier<T> operation) {
        if (transactionManager == null) return operation.get();
        org.springframework.transaction.support.TransactionTemplate transaction =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> operation.get());
    }

    @Transactional
    public CsvExport exportForSync(String key, String category, HttpSession session) throws IOException {
        return exportRows(key, category, session, true);
    }

    private CsvExport exportRows(String key, String category, HttpSession session, boolean forSync) throws IOException {
        Dataset data = dataset(key, category, session, false);
        if (forSync && data.type() == AppUser.class && !RoleAccess.isAdmin((String) session.getAttribute("role")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can sync user credentials");
        List<String> columns = columns(data);
        var catalog = references.catalog();
        List<String> warnings = new ArrayList<>();
        StringWriter output = new StringWriter();
        output.write('\ufeff');
        try (CSVPrinter printer = new CSVPrinter(output, CSVFormat.DEFAULT.builder().setHeader(columns.toArray(String[]::new)).build())) {
            for (Object item : data.rows().get()) {
                ObjectNode node = row(data, item);
                if (forSync && item instanceof AppUser user) node.put("password", users.passwordHashForSync(user));
                references.enrich(item, node, catalog, warnings);
                List<String> cells = new ArrayList<>();
                for (String column : columns) {
                    JsonNode cell = node.get(column);
                    String text = cell == null || cell.isNull() ? "" : cell.isContainerNode() ? cell.toString() : cell.asText();
                    cells.add(protectCell(text));
                }
                printer.printRecord(cells);
            }
        }
        return new CsvExport(1, output.toString(), warnings.stream().distinct().toList());
    }

    @Transactional
    public String templateCsv(String key, String category, HttpSession session) throws IOException {
        Dataset data = dataset(key, category, session, false);
        StringWriter output = new StringWriter();
        output.write('\ufeff');
        try (CSVPrinter printer = new CSVPrinter(output, CSVFormat.DEFAULT.builder()
                .setHeader(columns(data).toArray(String[]::new)).build())) {
            // A header-only file is a safe starting point for new records.
        }
        return output.toString();
    }

    // Prefix spreadsheet formulas and leading apostrophes; reverse exactly on import.
    static String protectCell(String value) {
        return !value.isEmpty() && "=+-@\t\r\n'".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }
    static String unprotectCell(String value) {
        return value.length() > 1 && value.charAt(0) == '\'' && "=+-@\t\r\n'".indexOf(value.charAt(1)) >= 0 ? value.substring(1) : value;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importCsv(String key, String category, String csv, HttpSession session) throws IOException {
        return importCsv(key, category, csv, session, false);
    }

    private Map<String, Object> importCsv(String key, String category, String csv, HttpSession session, boolean cloud) throws IOException {
        Dataset data = dataset(key, category, session, true);
        boolean cloudMaster = cloud && key.endsWith("-master");
        String masterCategory = Objects.toString(category, "").trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (cloud && key.endsWith("-master")) data.rows().get(); // retain category validation
        List<String> allowed = columns(data);
        var catalog = references.catalog();
        List<String> warnings = new ArrayList<>();
        if (csv.startsWith("\ufeff")) csv = csv.substring(1);
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setAllowMissingColumnNames(false).setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).build().parse(new StringReader(csv))) {
            List<String> headers = parser.getHeaderNames();
            if (!headers.contains("id")) throw new IllegalArgumentException("CSV must contain an id column. Export this page first to obtain its template.");
            for (String header : headers) if (!allowed.contains(header)) throw new IllegalArgumentException("Unknown column: " + header);
            Map<Long, ObjectNode> existing = new HashMap<>();
            Map<Long, Object> entities = new HashMap<>();
            for (Object item : cloud ? cloudWriter.rowsForImport(data.type(), category) : data.rows().get()) {
                ObjectNode node = row(data, item);
                if (cloudMaster) node.put("category", mapper.valueToTree(item).path("category").asText());
                references.enrich(item, node, catalog, new ArrayList<>());
                existing.put(node.path("id").asLong(), node);
                entities.put(node.path("id").asLong(), item);
            }
            Set<Long> seen = new HashSet<>();
            Set<Long> targetIds = new HashSet<>();
            List<Map.Entry<Long, ObjectNode>> pending = new ArrayList<>();
            for (CSVRecord record : parser) {
                try {
                    if (pending.size() >= 5000) throw new IllegalArgumentException("Maximum 5000 records per import");
                    if (!record.isConsistent()) throw new IllegalArgumentException("Column count does not match the header");
                    String rawId = record.get("id").trim();
                    Long id = rawId.isEmpty() || cloud && rawId.equalsIgnoreCase("null") ? null : Long.valueOf(rawId);
                    if (id != null && (id <= 0 || !seen.add(id))) throw new IllegalArgumentException("Invalid or duplicate id: " + id);
                    if (cloud && data.type() == AppUser.class && headers.contains("username")) {
                        String username = unprotectCell(record.get("username")).trim();
                        Optional<AppUser> usernameMatch = users.findCloudUserByUsername(username);
                        if (usernameMatch.isPresent()) id = usernameMatch.get().getId();
                        if (id != null && !targetIds.add(id)) throw new IllegalArgumentException("Multiple cloud users resolve to the same local account");
                    }
                    if (!cloud && id != null && !existing.containsKey(id)) throw new IllegalArgumentException("Unknown or inaccessible id: " + id + ". Leave id blank for a new record.");
                    boolean categoryChanged = cloudMaster && existing.containsKey(id)
                            && !existing.get(id).path("category").asText().equals(masterCategory);
                    ObjectNode node = !existing.containsKey(id) || categoryChanged ? mapper.createObjectNode() : existing.get(id).deepCopy();
                    for (String header : headers) {
                        String cell = unprotectCell(record.get(header));
                        if (header.equals("id")) { if (id == null) node.putNull(header); else node.put(header, id); }
                        else if (header.equals("observations")) node.set(header, cell.isBlank() ? mapper.createArrayNode() : mapper.readTree(cell));
                        else node.put(header, cell);
                    }
                    if (cloudMaster) node.put("category", masterCategory);
                    if (categoryChanged) warnings.add("Record " + id + ": incoming " + masterCategory + " replaces local master category " + existing.get(id).path("category").asText());
                    if (cloud) references.resolveCloud(data.type(), categoryChanged ? null : existing.get(id), node, new HashSet<>(headers), catalog, warnings);
                    else references.resolve(data.type(), id == null ? null : existing.get(id), node, new HashSet<>(headers), catalog, warnings);
                    if (!cloud && id != null) validateUpdate(data, existing.get(id), node);
                    pending.add(new AbstractMap.SimpleImmutableEntry<>(id, node));
                } catch (Exception ex) {
                    throw new IllegalArgumentException("CSV row " + (record.getRecordNumber() + 1) + ": " + ex.getMessage(), ex);
                }
            }
            if (pending.isEmpty() && !cloud) throw new IllegalArgumentException("CSV contains no data rows");
            if (cloudMaster && !pending.isEmpty()) {
                Set<Long> changedIds = new HashSet<>();
                for (var entry : pending) if (entry.getKey() != null && !equivalent(existing.get(entry.getKey()), entry.getValue()))
                    changedIds.add(entry.getKey());
                cloudWriter.prepareMasterImport(data.type(), masterCategory, pending.stream().map(Map.Entry::getValue).toList(), changedIds, warnings);
            }
            int created = 0, updated = 0, unchanged = 0;
            List<Map<String, Object>> generatedIds = new ArrayList<>();
            // Reserve supplied identities before generating any IDs from blank rows.
            List<Integer> writeOrder = java.util.stream.IntStream.range(0, pending.size()).boxed()
                    .sorted(Comparator.comparing(i -> pending.get(i).getKey() == null)).toList();
            for (int i : writeOrder) {
                var entry = pending.get(i);
                try {
                    if (entry.getKey() != null && equivalent(existing.get(entry.getKey()), entry.getValue())) {
                        references.store(entities.get(entry.getKey()), entry.getValue());
                        unchanged++;
                        continue;
                    }
                    if (cloud) {
                        long savedId = cloudWriter.save(data.type(), category, entry.getValue());
                        if (entry.getKey() == null) generatedIds.add(Map.of("csvRow", i + 2, "id", savedId));
                        if (data.type() == AppUser.class && value(entry.getValue(), "password").isBlank()
                                && (!existing.containsKey(entry.getKey()) || !value(entry.getValue(), "username")
                                .equalsIgnoreCase(value(existing.get(entry.getKey()), "username"))))
                            warnings.add("User " + value(entry.getValue(), "username") + " needs a local password reset; the cloud export did not include a password hash.");
                    }
                    else {
                        Object saved = data.save().apply(entry.getKey(), entry.getValue());
                        references.store(saved, entry.getValue());
                    }
                }
                catch (RuntimeException ex) { throw new IllegalArgumentException("CSV row " + (i + 2) + ": " + ex.getMessage(), ex); }
                if (!existing.containsKey(entry.getKey())) created++; else updated++;
            }
            return Map.of("status", "success", "created", created, "updated", updated, "unchanged", unchanged,
                    "generatedIds", generatedIds,
                    "warnings", warnings.stream().distinct().toList(),
                    "message", "Data sync completed: " + created + " created, " + updated + " updated, " + unchanged + " unchanged.");
        }
    }

    /** JSON counterpart used only by the authenticated internal sync API. */
    private Map<String, Object> importJson(String key, String category, ArrayNode records, HttpSession session, boolean cloud) {
        if (records == null) throw new IllegalArgumentException("JSON sync records are required");
        Dataset data = dataset(key, category, session, true);
        boolean cloudMaster = cloud && key.endsWith("-master");
        String masterCategory = Objects.toString(category, "").trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (cloudMaster) data.rows().get(); // retain category validation
        List<String> allowed = columns(data);
        Set<String> allowedSet = new HashSet<>(allowed);
        if (historyModule(key) != null) allowedSet.add("assignmentHistory");
        if (data.type() == GembaWalkRecord.class) allowedSet.addAll(List.of("createdAt", "updatedAt"));
        var catalog = references.catalog();
        List<String> warnings = new ArrayList<>();
        Map<Long, ObjectNode> existing = new HashMap<>();
        Map<Long, Object> entities = new HashMap<>();
        for (Object item : cloud ? cloudWriter.rowsForImport(data.type(), category) : data.rows().get()) {
            ObjectNode node = row(data, item);
            if (item instanceof GembaWalkRecord walk) {
                node.set("createdAt", mapper.valueToTree(walk.getCreatedAt()));
                node.set("updatedAt", mapper.valueToTree(walk.getUpdatedAt()));
            }
            if (cloudMaster) node.put("category", mapper.valueToTree(item).path("category").asText());
            references.enrich(item, node, catalog, new ArrayList<>());
            if (historyModule(key) != null) node.set("assignmentHistory", cloudWriter.assignmentHistory(historyModule(key), node.path("id").asLong()));
            existing.put(node.path("id").asLong(), node);
            entities.put(node.path("id").asLong(), item);
        }
        Set<Long> seen = new HashSet<>(), targetIds = new HashSet<>();
        List<Map.Entry<Long, ObjectNode>> pending = new ArrayList<>();
        for (int index = 0; index < records.size(); index++) {
            try {
                JsonNode source = records.get(index);
                if (!(source instanceof ObjectNode incoming)) throw new IllegalArgumentException("Record must be a JSON object");
                Iterator<String> names = incoming.fieldNames();
                while (names.hasNext()) {
                    String name = names.next();
                    if (!allowedSet.contains(name)) throw new IllegalArgumentException("Unknown field: " + name);
                }
                if (!incoming.has("id")) throw new IllegalArgumentException("Record has no id field");
                JsonNode idNode = incoming.get("id");
                if (idNode != null && !idNode.isNull() && (!idNode.isIntegralNumber() || !idNode.canConvertToLong()))
                    throw new IllegalArgumentException("Record ID must be an integer");
                Long id = idNode == null || idNode.isNull() ? null : idNode.isNumber() ? idNode.asLong() : parseLong(idNode.asText(), "id");
                if (id != null && (id <= 0 || !seen.add(id))) throw new IllegalArgumentException("Invalid or duplicate id: " + id);
                if (cloud && data.type() == AppUser.class && incoming.has("username")) {
                    String username = incoming.path("username").asText("").trim();
                    Optional<AppUser> usernameMatch = users.findCloudUserByUsername(username);
                    if (usernameMatch.isPresent()) id = usernameMatch.get().getId();
                    if (id != null && !targetIds.add(id)) throw new IllegalArgumentException("Multiple cloud users resolve to the same local account");
                }
                if (!cloud && id != null && !existing.containsKey(id)) throw new IllegalArgumentException("Unknown or inaccessible id: " + id);
                boolean categoryChanged = cloudMaster && existing.containsKey(id)
                        && !existing.get(id).path("category").asText().equals(masterCategory);
                ObjectNode node = !existing.containsKey(id) || categoryChanged ? mapper.createObjectNode() : existing.get(id).deepCopy();
                Set<String> fields = new HashSet<>();
                incoming.fields().forEachRemaining(entry -> {
                    fields.add(entry.getKey());
                    node.set(entry.getKey(), entry.getValue().deepCopy());
                });
                if (id == null) node.putNull("id"); else node.put("id", id);
                if (cloudMaster) node.put("category", masterCategory);
                if (categoryChanged) warnings.add("Record " + id + ": incoming " + masterCategory + " replaces local master category " + existing.get(id).path("category").asText());
                if (cloud) references.resolveCloud(data.type(), categoryChanged ? null : existing.get(id), node, fields, catalog, warnings);
                else references.resolve(data.type(), id == null ? null : existing.get(id), node, fields, catalog, warnings);
                if (!cloud && id != null) validateUpdate(data, existing.get(id), node);
                pending.add(new AbstractMap.SimpleImmutableEntry<>(id, node));
            } catch (Exception ex) {
                throw new IllegalArgumentException("JSON record " + (index + 1) + ": " + ex.getMessage(), ex);
            }
        }
        if (pending.isEmpty() && !cloud) throw new IllegalArgumentException("JSON sync batch contains no records");
        if (cloudMaster && !pending.isEmpty()) {
            Set<Long> changedIds = new HashSet<>();
            for (var entry : pending) if (entry.getKey() != null && !equivalent(existing.get(entry.getKey()), entry.getValue())) changedIds.add(entry.getKey());
            cloudWriter.prepareMasterImport(data.type(), masterCategory, pending.stream().map(Map.Entry::getValue).toList(), changedIds, warnings);
        }
        int created = 0, updated = 0, unchanged = 0;
        List<Map<String, Object>> generatedIds = new ArrayList<>();
        List<Integer> writeOrder = java.util.stream.IntStream.range(0, pending.size()).boxed()
                .sorted(Comparator.comparing(i -> pending.get(i).getKey() == null)).toList();
        for (int index : writeOrder) {
            var entry = pending.get(index);
            try {
                if (entry.getKey() != null && equivalent(existing.get(entry.getKey()), entry.getValue())) {
                    if (!cloud) references.store(entities.get(entry.getKey()), entry.getValue());
                    unchanged++;
                    continue;
                }
                if (cloud) {
                    long savedId = cloudWriter.save(data.type(), category, entry.getValue());
                    if (entry.getKey() == null) generatedIds.add(Map.of("index", index, "id", savedId));
                    if (data.type() == AppUser.class && value(entry.getValue(), "password").isBlank()
                            && (!existing.containsKey(entry.getKey()) || !value(entry.getValue(), "username")
                            .equalsIgnoreCase(value(existing.get(entry.getKey()), "username"))))
                        warnings.add("User " + value(entry.getValue(), "username") + " needs a local password reset; the Cloud export did not include a password hash.");
                } else {
                    Object saved = data.save().apply(entry.getKey(), entry.getValue());
                    references.store(saved, entry.getValue());
                }
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("JSON record " + (index + 1) + ": " + ex.getMessage(), ex);
            }
            if (!existing.containsKey(entry.getKey())) created++; else updated++;
        }
        return Map.of("status", "success", "created", created, "updated", updated, "unchanged", unchanged,
                "generatedIds", generatedIds, "warnings", warnings.stream().distinct().toList(),
                "message", "Data sync completed: " + created + " created, " + updated + " updated, " + unchanged + " unchanged.");
    }

    private static long parseLong(String value, String field) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid " + field); }
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importCsvForSync(String key, String category, String csv) throws IOException {
        HttpSession session = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class}, (proxy, method, args) -> {
                    if ("getAttribute".equals(method.getName())) {
                        return switch (String.valueOf(args[0])) {
                            case "username" -> "systemadmin";
                            case "role" -> RoleAccess.ADMIN;
                            case "viewPermissions", "editPermissions" -> RoleAccess.CONFIG_PAGES;
                            default -> null;
                        };
                    }
                    if ("getAttributeNames".equals(method.getName())) return java.util.Collections.emptyEnumeration();
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                });
        return importCsv(key, category, csv, session, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importJsonFromCloud(String cloudInstanceId, String key, String category, ArrayNode records) {
        if (!Set.of("gemba-walk", "gemba-kaizen", "abnormality", "process-confirmation").contains(key))
            return importJsonForSync(key, category, records);
        String datasetKey = key + (category == null || category.isBlank() ? "" : ":" + category);
        Set<Long> occupied = new HashSet<>();
        for (Object row : cloudWriter.rows(dataset(key, category, syncSession(), true).type(), category)) {
            long id = mapper.valueToTree(row).path("id").asLong();
            if (id > 0) occupied.add(id);
        }
        List<Long> sourceIds = new ArrayList<>(), targetIds = new ArrayList<>();
        ArrayNode rewritten = mapper.createArrayNode();
        for (JsonNode source : records) {
            long sourceId = source.path("id").asLong(0);
            if (sourceId <= 0) throw new IllegalArgumentException("Cloud report has an invalid record ID");
            SyncRecordMapping mapping = syncMappings.findByPeerInstanceIdAndDatasetKeyAndPeerRecordId(cloudInstanceId, datasetKey, sourceId).orElse(null);
            Long localId = mapping == null ? (occupied.contains(sourceId) ? null : sourceId) : mapping.getLocalRecordId();
            ObjectNode copy = (ObjectNode) source.deepCopy();
            if (localId == null) copy.putNull("id"); else copy.put("id", localId);
            stripNestedRecordIds(copy);
            rewritten.add(copy); sourceIds.add(sourceId); targetIds.add(localId);
        }
        Map<String, Object> result = importJsonForSync(key, category, rewritten);
        Map<Integer, Long> generated = generatedJsonIds(result);
        for (int index = 0; index < sourceIds.size(); index++) {
            Long localId = targetIds.get(index);
            if (localId == null) localId = generated.get(index);
            if (localId == null) throw new IllegalStateException("Cloud import did not return an ID for record " + sourceIds.get(index));
            SyncRecordMapping mapping = syncMappings.findByPeerInstanceIdAndDatasetKeyAndPeerRecordId(cloudInstanceId, datasetKey, sourceIds.get(index)).orElseGet(SyncRecordMapping::new);
            mapping.setPeerInstanceId(cloudInstanceId); mapping.setDatasetKey(datasetKey);
            mapping.setPeerRecordId(sourceIds.get(index)); mapping.setLocalRecordId(localId); syncMappings.save(mapping);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importJsonFromPeer(String peerInstanceId, String key, String category, ArrayNode records,
                                                   Map<Long, Long> knownPeerToLocalIds) {
        if (!Set.of("gemba-walk", "gemba-kaizen", "abnormality", "process-confirmation").contains(key))
            throw new IllegalArgumentException("Only reporting datasets can be published from On-Prem to Cloud");
        if (peerInstanceId == null || !peerInstanceId.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid peer installation ID");
        String datasetKey = key + (category == null || category.isBlank() ? "" : ":" + category);
        List<Long> sourceIds = new ArrayList<>(), targetIds = new ArrayList<>();
        ArrayNode rewritten = mapper.createArrayNode();
        for (JsonNode source : records) {
            long sourceId = source.path("id").asLong(0);
            if (sourceId <= 0) throw new IllegalArgumentException("On-Prem report rows must have a positive ID");
            Long targetId = syncMappings.findByPeerInstanceIdAndDatasetKeyAndPeerRecordId(peerInstanceId, datasetKey, sourceId)
                    .map(SyncRecordMapping::getLocalRecordId).orElse(knownPeerToLocalIds == null ? null : knownPeerToLocalIds.get(sourceId));
            ObjectNode copy = (ObjectNode) source.deepCopy();
            if (targetId == null) copy.putNull("id"); else copy.put("id", targetId);
            stripNestedRecordIds(copy);
            rewritten.add(copy); sourceIds.add(sourceId); targetIds.add(targetId);
        }
        Map<String, Object> result = importJsonForSync(key, category, rewritten);
        Map<Integer, Long> generated = generatedJsonIds(result);
        Map<String, Long> mappings = new LinkedHashMap<>();
        for (int index = 0; index < sourceIds.size(); index++) {
            Long localId = targetIds.get(index);
            if (localId == null) localId = generated.get(index);
            if (localId == null) throw new IllegalStateException("Cloud did not return an ID for source record " + sourceIds.get(index));
            SyncRecordMapping mapping = syncMappings.findByPeerInstanceIdAndDatasetKeyAndPeerRecordId(peerInstanceId, datasetKey, sourceIds.get(index)).orElseGet(SyncRecordMapping::new);
            mapping.setPeerInstanceId(peerInstanceId); mapping.setDatasetKey(datasetKey);
            mapping.setPeerRecordId(sourceIds.get(index)); mapping.setLocalRecordId(localId); syncMappings.save(mapping);
            mappings.put(String.valueOf(sourceIds.get(index)), localId);
        }
        Map<String, Object> response = new LinkedHashMap<>(result); response.put("idMappings", mappings); return response;
    }

    private Map<Integer, Long> generatedJsonIds(Map<String, Object> result) {
        Map<Integer, Long> generated = new HashMap<>();
        if (result.get("generatedIds") instanceof List<?> list) for (Object item : list) if (item instanceof Map<?, ?> map
                && map.get("index") instanceof Number index && map.get("id") instanceof Number id) generated.put(index.intValue(), id.longValue());
        return generated;
    }

    private void stripNestedRecordIds(ObjectNode record) {
        for (String field : List.of("observations", "zmObservationsJson", "pmObservationsJson", "qmObservationsJson")) {
            JsonNode value = record.get(field);
            if (value == null || value.isNull()) continue;
            if (value.isTextual()) {
                if (value.asText().isBlank()) continue;
                try { JsonNode nested = mapper.readTree(value.asText()); if (nested != null) { stripNestedIds(nested); record.put(field, nested.toString()); } }
                catch (IOException ex) { throw new IllegalArgumentException("Invalid nested JSON in " + field, ex); }
            } else stripNestedIds(value);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteMissingForSync(String key, String category, ArrayNode records) {
        return deleteMissingForSync(key, category, records, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteMissingForSync(String key, String category, ArrayNode records, String peerInstanceId) {
        Dataset data = dataset(key, category, syncSession(), true);
        if (data.type() == AppUser.class) {
            Set<String> usernames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (JsonNode row : records) usernames.add(row.path("username").asText(""));
            return cloudWriter.deleteMissingUsers(usernames);
        }
        Set<Long> ids = new HashSet<>();
        boolean mappedReports = peerInstanceId != null && historyModule(key) != null;
        String datasetKey = key + (category == null || category.isBlank() ? "" : ":" + category);
        for (JsonNode row : records) {
            long id = row.path("id").asLong(0);
            if (id <= 0) continue;
            if (mappedReports) {
                SyncRecordMapping mapping = syncMappings.findByPeerInstanceIdAndDatasetKeyAndPeerRecordId(peerInstanceId, datasetKey, id)
                        .orElseThrow(() -> new IllegalStateException("Missing local mapping for synced record " + id));
                ids.add(mapping.getLocalRecordId());
            } else ids.add(id);
        }
        return cloudWriter.deleteMissing(data.type(), category, ids);
    }

    /** Publish report rows from a peer using stable per-installation ID mappings. */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importCsvFromPeer(String peerInstanceId, String key, String category, String csv,
                                                  Map<Long, Long> knownPeerToLocalIds) throws IOException {
        if (!Set.of("gemba-walk", "gemba-kaizen", "abnormality", "process-confirmation").contains(key))
            throw new IllegalArgumentException("Only reporting datasets can be published from On-Prem to Cloud");
        if (peerInstanceId == null || !peerInstanceId.matches("[0-9a-fA-F-]{36}"))
            throw new IllegalArgumentException("Invalid peer installation ID");
        String datasetKey = key + (category == null || category.isBlank() ? "" : ":" + category);
        PeerCsv rewritten = rewritePeerCsv(peerInstanceId, datasetKey, csv, knownPeerToLocalIds);
        Map<String, Object> result = importCsvForSync(key, category, rewritten.csv());
        List<?> generated = result.get("generatedIds") instanceof List<?> list ? list : List.of();
        Map<Integer, Long> generatedByRow = new HashMap<>();
        for (Object item : generated) if (item instanceof Map<?, ?> map) {
            Object row = map.get("csvRow"), id = map.get("id");
            if (row instanceof Number rowNumber && id instanceof Number idNumber)
                generatedByRow.put(rowNumber.intValue(), idNumber.longValue());
        }
        for (int i = 0; i < rewritten.sourceIds().size(); i++) {
            Long sourceId = rewritten.sourceIds().get(i);
            Long localId = rewritten.targetIds().get(i);
            if (localId == null) localId = generatedByRow.get(i + 2);
            if (localId == null) throw new IllegalStateException("Cloud did not return an ID for source record " + sourceId);
            SyncRecordMapping mapping = syncMappings.findByPeerInstanceIdAndDatasetKeyAndPeerRecordId(
                    peerInstanceId, datasetKey, sourceId).orElseGet(SyncRecordMapping::new);
            mapping.setPeerInstanceId(peerInstanceId);
            mapping.setDatasetKey(datasetKey);
            mapping.setPeerRecordId(sourceId);
            mapping.setLocalRecordId(localId);
            syncMappings.save(mapping);
        }
        Map<String, Object> response = new LinkedHashMap<>(result);
        Map<String, Long> idMappings = new LinkedHashMap<>();
        for (Long sourceId : rewritten.sourceIds()) {
            syncMappings.findByPeerInstanceIdAndDatasetKeyAndPeerRecordId(peerInstanceId, datasetKey, sourceId)
                    .ifPresent(value -> idMappings.put(String.valueOf(sourceId), value.getLocalRecordId()));
        }
        response.put("idMappings", idMappings);
        return response;
    }

    /** Import a Cloud report snapshot while keeping previously mapped local identities stable. */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importCsvFromCloud(String cloudInstanceId, String key, String category, String csv) throws IOException {
        if (!Set.of("gemba-walk", "gemba-kaizen", "abnormality", "process-confirmation").contains(key))
            return importCsvForSync(key, category, csv);
        Dataset data = dataset(key, category, syncSession(), true);
        String datasetKey = key + (category == null || category.isBlank() ? "" : ":" + category);
        Set<Long> occupied = new HashSet<>();
        for (Object row : cloudWriter.rows(data.type(), category)) {
            long id = mapper.valueToTree(row).path("id").asLong();
            if (id > 0) occupied.add(id);
        }
        if (csv.startsWith("\ufeff")) csv = csv.substring(1);
        StringWriter output = new StringWriter();
        List<Long> sourceIds = new ArrayList<>();
        List<Long> localIds = new ArrayList<>();
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setAllowMissingColumnNames(false).setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).build().parse(new StringReader(csv));
             CSVPrinter printer = new CSVPrinter(output, CSVFormat.DEFAULT.builder()
                     .setHeader(parser.getHeaderNames().toArray(String[]::new)).build())) {
            if (!parser.getHeaderNames().contains("id")) throw new IllegalArgumentException("Cloud CSV has no id column");
            for (CSVRecord row : parser) {
                if (!row.isConsistent()) throw new IllegalArgumentException("Cloud CSV contains an inconsistent row");
                long sourceId = Long.parseLong(row.get("id"));
                SyncRecordMapping mapping = syncMappings.findByPeerInstanceIdAndDatasetKeyAndPeerRecordId(
                        cloudInstanceId, datasetKey, sourceId).orElse(null);
                Long localId = mapping == null ? (occupied.contains(sourceId) ? null : sourceId) : mapping.getLocalRecordId();
                List<String> cells = new ArrayList<>();
                for (String header : parser.getHeaderNames()) {
                    String cell = row.get(header);
                    if (header.equals("id")) cell = localId == null ? "" : localId.toString();
                    else if (isNestedObservationColumn(header)) cell = stripNestedObservationIds(cell);
                    cells.add(cell);
                }
                printer.printRecord(cells);
                sourceIds.add(sourceId);
                localIds.add(localId);
            }
        }
        Map<String, Object> result = importCsvForSync(key, category, output.toString());
        Map<Integer, Long> generated = generatedIdsByRow(result);
        for (int i = 0; i < sourceIds.size(); i++) {
            Long localId = localIds.get(i);
            if (localId == null) localId = generated.get(i + 2);
            if (localId == null) throw new IllegalStateException("Cloud import did not return an ID for record " + sourceIds.get(i));
            SyncRecordMapping mapping = syncMappings.findByPeerInstanceIdAndDatasetKeyAndPeerRecordId(
                    cloudInstanceId, datasetKey, sourceIds.get(i)).orElseGet(SyncRecordMapping::new);
            mapping.setPeerInstanceId(cloudInstanceId); mapping.setDatasetKey(datasetKey);
            mapping.setPeerRecordId(sourceIds.get(i)); mapping.setLocalRecordId(localId);
            syncMappings.save(mapping);
        }
        return result;
    }

    @Transactional
    public void updatePeerHashes(String cloudInstanceId, String key, String category) throws IOException {
        String datasetKey = key + (category == null || category.isBlank() ? "" : ":" + category);
        for (JsonNode row : exportJsonForSync(key, category).records()) {
            long id = row.path("id").asLong();
            syncMappings.findByPeerInstanceIdAndDatasetKeyAndLocalRecordId(cloudInstanceId, datasetKey, id)
                    .ifPresent(mapping -> { mapping.setLocalHash(hashJsonRecord((ObjectNode) row)); syncMappings.save(mapping); });
        }
    }

    public List<ObjectNode> exportJsonForOnPremPush(String key, String category) {
        List<ObjectNode> rows = new ArrayList<>();
        exportJsonForSync(key, category).records().forEach(row -> rows.add((ObjectNode) row.deepCopy()));
        return rows;
    }

    public String hashJsonRecord(ObjectNode source) {
        ObjectNode canonical = source.deepCopy();
        canonical.remove("id");
        for (String field : List.of("plantId", "departmentId", "departmentIds", "processAreaId", "areaIds", "designationId",
                "parentPlantId", "parentDepartmentId", "classificationOfKaizenId", "typeOfTagId", "abnormalityDefectTypeId",
                "gembaCategoryId", "lifeSaverRuleId")) canonical.remove(field);
        stripNestedRecordIds(canonical);
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(canonical))); }
        catch (java.security.NoSuchAlgorithmException | IOException ex) { throw new IllegalStateException(ex); }
    }

    private record PeerRowHash(long id, String hash) { }

    private List<PeerRowHash> rowHashes(String csv) throws IOException {
        if (csv.startsWith("\ufeff")) csv = csv.substring(1);
        List<PeerRowHash> result = new ArrayList<>();
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(new StringReader(csv))) {
            for (CSVRecord row : parser) {
                long id = Long.parseLong(row.get("id"));
                StringBuilder canonical = new StringBuilder();
                for (String header : parser.getHeaderNames()) {
                    if (header.equals("id") || isReferenceIdColumn(header)) continue;
                    String cell = row.get(header);
                    if (isNestedObservationColumn(header) && !cell.isBlank()) cell = stripNestedObservationIds(cell);
                    canonical.append(header.length()).append(':').append(header).append(cell.length()).append(':').append(cell).append('\n');
                }
                String hash;
                try { hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
                catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
                result.add(new PeerRowHash(id, hash));
            }
        }
        return result;
    }

    private Map<Integer, Long> generatedIdsByRow(Map<String, Object> result) {
        Map<Integer, Long> generated = new HashMap<>();
        if (result.get("generatedIds") instanceof List<?> list) for (Object item : list) if (item instanceof Map<?, ?> map) {
            if (map.get("csvRow") instanceof Number row && map.get("id") instanceof Number id)
                generated.put(row.intValue(), id.longValue());
        }
        return generated;
    }

    public CsvExport exportForOnPremPush(String key, String category) throws IOException {
        return exportForSync(key, category, syncSession());
    }

    private record PeerCsv(String csv, List<Long> sourceIds, List<Long> targetIds) { }

    private PeerCsv rewritePeerCsv(String peerInstanceId, String datasetKey, String csv,
                                   Map<Long, Long> knownPeerToLocalIds) throws IOException {
        if (csv.startsWith("\ufeff")) csv = csv.substring(1);
        List<Long> sourceIds = new ArrayList<>();
        List<Long> targetIds = new ArrayList<>();
        StringWriter output = new StringWriter();
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setAllowMissingColumnNames(false).setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
                .build().parse(new StringReader(csv));
             CSVPrinter printer = new CSVPrinter(output, CSVFormat.DEFAULT.builder()
                     .setHeader(parser.getHeaderNames().toArray(String[]::new)).build())) {
            if (!parser.getHeaderNames().contains("id")) throw new IllegalArgumentException("CSV must contain an id column");
            for (CSVRecord record : parser) {
                if (!record.isConsistent()) throw new IllegalArgumentException("CSV contains an inconsistent row");
                String rawId = record.get("id").trim();
                long sourceId;
                try { sourceId = Long.parseLong(rawId); }
                catch (NumberFormatException ex) { throw new IllegalArgumentException("On-Prem report rows must have a positive ID"); }
                if (sourceId <= 0) throw new IllegalArgumentException("On-Prem report rows must have a positive ID");
                Long targetId = syncMappings.findByPeerInstanceIdAndDatasetKeyAndPeerRecordId(peerInstanceId, datasetKey, sourceId)
                        .map(SyncRecordMapping::getLocalRecordId)
                        .orElse(knownPeerToLocalIds == null ? null : knownPeerToLocalIds.get(sourceId));
                List<String> cells = new ArrayList<>();
                for (String header : parser.getHeaderNames()) {
                    String cell = record.get(header);
                    if (header.equals("id")) cell = targetId == null ? "" : String.valueOf(targetId);
                    else if (isNestedObservationColumn(header)) cell = stripNestedObservationIds(cell);
                    cells.add(cell);
                }
                printer.printRecord(cells);
                sourceIds.add(sourceId);
                targetIds.add(targetId);
            }
        }
        return new PeerCsv(output.toString(), sourceIds, targetIds);
    }

    private String stripNestedObservationIds(String value) throws IOException {
        if (value == null || value.isBlank()) return value;
        JsonNode root = mapper.readTree(value);
        stripNestedIds(root);
        return root.toString();
    }

    private static void stripNestedIds(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.remove("id");
            object.elements().forEachRemaining(DataSyncService::stripNestedIds);
        } else if (node != null && node.isArray()) node.elements().forEachRemaining(DataSyncService::stripNestedIds);
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

    /**
     * Applies authoritative cloud deletion after every file in a sync run imported successfully.
     * Keeping this separate from import prevents an incomplete run from deleting local data.
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteMissingForSync(String key, String category, String csv) throws IOException {
        Dataset data = dataset(key, category, syncSession(), true);
        if (data.type() == AppUser.class) return cloudWriter.deleteMissingUsers(sourceUsernames(csv));
        Set<Long> sourceIds = sourceIds(csv);
        return cloudWriter.deleteMissing(data.type(), category, sourceIds);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteUsersForCloudReplacement() {
        return cloudWriter.deleteAllUsersForCloudReplacement();
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceUsersForCloudSync(String csv) throws IOException {
        validateCloudUsersCsv(csv);
        deleteUsersForCloudReplacement();
        importCsv("users", "", csv, syncSession(), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public int validateCloudUsersCsv(String csv) throws IOException {
        Dataset data = dataset("users", "", syncSession(), true);
        if (csv.startsWith("\ufeff")) csv = csv.substring(1);
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setAllowMissingColumnNames(false).setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
                .build().parse(new StringReader(csv))) {
            List<String> headers = parser.getHeaderNames();
            if (!headers.containsAll(List.of("id", "username", "email", "role")))
                throw new IllegalArgumentException("Cloud users CSV must include id, username, email and role columns");
            Set<String> usernames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            Set<String> emails = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            Set<String> employeeIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            Set<Long> ids = new HashSet<>();
            for (CSVRecord record : parser) {
                if (!record.isConsistent()) throw new IllegalArgumentException("Cloud users CSV has an inconsistent row");
                String username = unprotectCell(record.get("username")).trim();
                String email = unprotectCell(record.get("email")).trim();
                String role = unprotectCell(record.get("role")).trim();
                if (username.isBlank() || email.isBlank()) throw new IllegalArgumentException("Cloud users CSV contains a blank username or email");
                if (!usernames.add(username) || !emails.add(email)) throw new IllegalArgumentException("Cloud users CSV contains duplicate usernames or emails");
                if (!role.equalsIgnoreCase("Admin") && !role.equalsIgnoreCase("User"))
                    throw new IllegalArgumentException("Cloud users CSV has an unsupported role for " + username);
                String rawId = record.get("id").trim();
                if (rawId.isBlank() || rawId.equalsIgnoreCase("null")) throw new IllegalArgumentException("Cloud users CSV contains a user without an ID");
                long id;
                try { id = Long.parseLong(rawId); }
                catch (NumberFormatException ex) { throw new IllegalArgumentException("Cloud users CSV contains an invalid user ID: " + rawId); }
                if (id <= 0 || !ids.add(id)) throw new IllegalArgumentException("Cloud users CSV contains an invalid or duplicate user ID: " + rawId);
                if (headers.contains("employeeId")) {
                    String employeeId = unprotectCell(record.get("employeeId")).trim();
                    if (!employeeId.isBlank() && !employeeIds.add(employeeId))
                        throw new IllegalArgumentException("Cloud users CSV contains duplicate employee IDs");
                }
            }
            return ids.size();
        }
    }

    private static Set<String> sourceUsernames(String csv) throws IOException {
        if (csv.startsWith("\ufeff")) csv = csv.substring(1);
        Set<String> usernames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setAllowMissingColumnNames(false).setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).build().parse(new StringReader(csv))) {
            if (!parser.getHeaderNames().contains("username")) throw new IllegalArgumentException("Cloud users snapshot has no username column");
            for (CSVRecord record : parser) {
                if (!record.isConsistent()) throw new IllegalArgumentException("Cloud users snapshot contains an inconsistent row");
                String username = unprotectCell(record.get("username")).trim();
                if (username.isBlank() || !usernames.add(username)) throw new IllegalArgumentException("Cloud users snapshot contains a blank or duplicate username");
            }
        }
        return usernames;
    }

    private static Set<Long> sourceIds(String csv) throws IOException {
        if (csv.startsWith("\ufeff")) csv = csv.substring(1);
        Set<Long> ids = new HashSet<>();
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setAllowMissingColumnNames(false).setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).build().parse(new StringReader(csv))) {
            if (!parser.getHeaderNames().contains("id")) throw new IllegalArgumentException("Cloud snapshot has no id column");
            for (CSVRecord record : parser) {
                if (!record.isConsistent()) throw new IllegalArgumentException("Cloud snapshot contains an inconsistent row");
                String rawId = record.get("id").trim();
                if (rawId.isBlank()) throw new IllegalArgumentException("Cloud snapshot contains a row without an id");
                long id = Long.parseLong(rawId);
                if (id <= 0 || !ids.add(id)) throw new IllegalArgumentException("Cloud snapshot contains an invalid or duplicate id: " + rawId);
            }
        }
        return ids;
    }

    private static HttpSession syncSession() {
        return (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(), new Class<?>[]{HttpSession.class}, (proxy, method, args) -> {
            if ("getAttribute".equals(method.getName())) return switch (String.valueOf(args[0])) {
                case "username" -> "systemadmin";
                case "role" -> RoleAccess.ADMIN;
                case "viewPermissions", "editPermissions" -> RoleAccess.CONFIG_PAGES;
                default -> null;
            };
            if ("getAttributeNames".equals(method.getName())) return java.util.Collections.emptyEnumeration();
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            return null;
        });
    }

    private static String value(ObjectNode node, String key) { return node.path(key).asText(""); }
    private boolean equivalent(JsonNode first, JsonNode second) {
        if (first == null || first.isNull()) return second == null || second.isNull() || second.isTextual() && second.asText().isEmpty();
        if (second == null || second.isNull()) return first.isTextual() && first.asText().isEmpty();
        if (first.isObject() && second.isObject()) {
            Set<String> fields = new HashSet<>(); first.fieldNames().forEachRemaining(fields::add); second.fieldNames().forEachRemaining(fields::add);
            return fields.stream().allMatch(field -> equivalentField(field, first.get(field), second.get(field)));
        }
        if (first.isArray() && second.isArray()) {
            if (first.size() != second.size()) return false;
            for (int i = 0; i < first.size(); i++) if (!equivalent(first.get(i), second.get(i))) return false;
            return true;
        }
        return first.asText().equals(second.asText());
    }

    private boolean equivalentField(String field, JsonNode first, JsonNode second) {
        if (Set.of("observations", "zmObservationsJson", "pmObservationsJson", "qmObservationsJson").contains(field)) {
            try {
                JsonNode left = parseNestedJson(first);
                JsonNode right = parseNestedJson(second);
                stripNestedIds(left);
                stripNestedIds(right);
                return equivalent(left, right);
            } catch (IOException ex) { return false; }
        }
        return equivalent(first, second);
    }

    private JsonNode parseNestedJson(JsonNode value) throws IOException {
        if (value == null || value.isNull()) return mapper.createArrayNode();
        if (value.isContainerNode()) return value.deepCopy();
        String json = value.asText();
        return json.isBlank() ? mapper.createArrayNode() : mapper.readTree(json);
    }

    private void validateUpdate(Dataset data, ObjectNode original, ObjectNode incoming) {
        List<String> readOnly = List.of();
        if (data.type() == AppUser.class) readOnly = List.of("username");
        if (data.type() == GembaKaizenRecord.class) readOnly = List.of("name", "lastModifiedTime", "gembaKaizenProviderName",
                "employeeIdHoNumber", "department", "classificationOfKaizen", "gembaKaizenLocation", "gembaKaizenGenerationDate", "kaizenIdea", "benefitsOfKaizen");
        if (data.type() == AbnormalityReportingRecord.class) readOnly = List.of("abnormalityTagNumber", "tagRaisedBy");
        if (data.type() == CarlexProcessConfirmation.class) readOnly = List.of("name", "email", "lastModifiedTime", "processConfirmationDoneBy");
        if (data.type() == GembaWalkRecord.class) readOnly = List.of("scheduleItemId", "startTime", "completionTime", "email", "managerName",
                "createdBy", "creatorDepartment", "creatorArea", "dateOfLeadershipSafetyWalkConducted", "managementSafetyWalkWeek", "locationOfMswConducted", "createdAt", "updatedAt");
        for (String field : readOnly) {
            if (!value(original, field).equals(value(incoming, field)))
                throw new IllegalArgumentException(field + " is read-only for existing records");
        }
        for (var link : references.links(data.type())) {
            if (readOnly.contains(link.field()) && !value(original, link.column()).isBlank()
                    && !value(original, link.column()).equals(value(incoming, link.column())))
                throw new IllegalArgumentException(link.column() + " is read-only for existing records");
        }
        if (data.type() == GembaWalkRecord.class) {
            JsonNode before = original.path("observations"), after = incoming.path("observations");
            if (!after.isArray() || before.size() != after.size())
                throw new IllegalArgumentException("Existing Gemba Walk observations cannot be added or removed");
            Map<Long, JsonNode> byId = new HashMap<>();
            for (JsonNode item : after) {
                long id = item.path("id").asLong();
                if (id <= 0 || byId.put(id, item) != null) throw new IllegalArgumentException("Each observation must retain its unique id");
            }
            var ordered = mapper.createArrayNode();
            for (JsonNode item : before) {
                JsonNode match = byId.get(item.path("id").asLong());
                if (match == null) throw new IllegalArgumentException("Unknown observation id");
                for (String field : List.of("observationOrder", "observationDescription", "gembaCategory", "lifeSaverRule")) {
                    if (!item.path(field).asText("").equals(match.path(field).asText("")))
                        throw new IllegalArgumentException("Observation " + field + " is read-only for existing records");
                }
                for (String field : List.of("gembaCategoryId", "lifeSaverRuleId")) {
                    if (!item.path(field).asText("").isBlank() && !item.path(field).asText("").equals(match.path(field).asText("")))
                        throw new IllegalArgumentException("Observation " + field + " is read-only for existing records");
                }
                ordered.add(match);
            }
            incoming.set("observations", ordered);
        }
    }
    private Set<String> permissions(ObjectNode node, String key) {
        return new HashSet<>(Arrays.asList(value(node, key).split(",")));
    }
    private Object saveUser(Long id, ObjectNode n) {
        Optional<String> error = id == null
            ? users.addUser(value(n, "username"), value(n, "name"), value(n, "employeeId"), value(n, "department"), value(n, "area"), value(n, "plant"),
                value(n, "designation"), value(n, "reportingManager"), value(n, "email"), value(n, "password"), value(n, "role"), value(n, "status"), permissions(n, "pageViewPermissions"), permissions(n, "pageEditPermissions"))
            : users.updateUser(id, value(n, "name"), value(n, "employeeId"), value(n, "department"), value(n, "area"), value(n, "plant"),
                value(n, "designation"), value(n, "reportingManager"), value(n, "email"), value(n, "password"), value(n, "role"), value(n, "status"), permissions(n, "pageViewPermissions"), permissions(n, "pageEditPermissions"));
        error.ifPresent(message -> { throw new IllegalArgumentException(message); });
        return users.getManageableUsers().stream().filter(user -> id == null ? user.getUsername().equals(value(n, "username")) : user.getId().equals(id)).findFirst().orElse(null);
    }
}
