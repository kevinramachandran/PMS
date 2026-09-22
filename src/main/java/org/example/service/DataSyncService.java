package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.csv.*;
import org.example.entity.*;
import org.example.util.RoleAccess;
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
        return exportForSync(key, category, session).csv();
    }

    public record CsvExport(int version, String csv, List<String> warnings) { }

    @Transactional
    public CsvExport exportForSync(String key, String category, HttpSession session) throws IOException {
        Dataset data = dataset(key, category, session, false);
        List<String> columns = columns(data);
        var catalog = references.catalog();
        List<String> warnings = new ArrayList<>();
        StringWriter output = new StringWriter();
        output.write('\ufeff');
        try (CSVPrinter printer = new CSVPrinter(output, CSVFormat.DEFAULT.builder().setHeader(columns.toArray(String[]::new)).build())) {
            for (Object item : data.rows().get()) {
                ObjectNode node = row(data, item);
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
            for (Object item : cloud ? cloudWriter.rows(data.type(), category) : data.rows().get()) {
                ObjectNode node = row(data, item);
                if (cloudMaster) node.put("category", mapper.valueToTree(item).path("category").asText());
                references.enrich(item, node, catalog, new ArrayList<>());
                existing.put(node.path("id").asLong(), node);
                entities.put(node.path("id").asLong(), item);
            }
            Set<Long> seen = new HashSet<>();
            List<Map.Entry<Long, ObjectNode>> pending = new ArrayList<>();
            for (CSVRecord record : parser) {
                try {
                    if (pending.size() >= 5000) throw new IllegalArgumentException("Maximum 5000 records per import");
                    if (!record.isConsistent()) throw new IllegalArgumentException("Column count does not match the header");
                    String rawId = record.get("id").trim();
                    Long id = rawId.isEmpty() || cloud && rawId.equalsIgnoreCase("null") ? null : Long.valueOf(rawId);
                    if (id != null && (id <= 0 || !seen.add(id))) throw new IllegalArgumentException("Invalid or duplicate id: " + id);
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
                            warnings.add("User " + value(entry.getValue(), "username") + " needs a local password reset; cloud exports omit passwords.");
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
        if (Set.of("zmObservationsJson", "pmObservationsJson", "qmObservationsJson").contains(field)) {
            try {
                String left = first == null || first.isNull() ? "" : first.asText();
                String right = second == null || second.isNull() ? "" : second.asText();
                return equivalent(left.isBlank() ? mapper.createArrayNode() : mapper.readTree(left),
                        right.isBlank() ? mapper.createArrayNode() : mapper.readTree(right));
            } catch (IOException ex) { return false; }
        }
        return equivalent(first, second);
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
