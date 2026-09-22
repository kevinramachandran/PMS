package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.example.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/** Resolves only exact, scoped matches. Names remain historical snapshots on existing links. */
@Service
public class MasterReferenceService {
    private final EntityManager em;
    private final ObjectMapper mapper;
    public MasterReferenceService(EntityManager em, ObjectMapper mapper) { this.em = em; this.mapper = mapper; }
    public record Link(String field, String column, Class<?> master, String category, boolean many) {}
    public record Master(Long id, String category, String name, String plant, String department) {}
    public record Catalog(Map<Class<?>, List<Master>> rows) {
        List<Master> category(Link link) { return rows.getOrDefault(link.master(), List.of()).stream().filter(m -> m.category().equals(link.category())).toList(); }
    }
    private static Link plant(String field, String column, String category) { return new Link(field, column, PlantMasterDataItem.class, category, false); }
    public List<Link> links(Class<?> type) {
        var result = new ArrayList<Link>();
        if (type == PlantMasterDataItem.class) return List.of(plant("parentPlant", "parentPlantId", "PLANT"), plant("parentDepartment", "parentDepartmentId", "DEPARTMENT"));
        if (type == GembaWalkObservation.class) return List.of(
                new Link("gembaCategory", "gembaCategoryId", GembaWalkMasterDataItem.class, "GEMBA_CATEGORY", false),
                new Link("lifeSaverRule", "lifeSaverRuleId", GembaWalkMasterDataItem.class, "LIFE_SAVER_RULE", false));
        if (type == AppUser.class) {
            return List.of(plant("plant", "plantId", "PLANT"), plant("department", "departmentId", "DEPARTMENT"),
                    new Link("area", "areaIds", PlantMasterDataItem.class, "PROCESS_AREA", true), plant("designation", "designationId", "DESIGNATION"));
        }
        if (!List.of(GembaWalkRecord.class, GembaKaizenRecord.class, AbnormalityReportingRecord.class, CarlexProcessConfirmation.class).contains(type)) return result;
        // The virtual plant scope disambiguates equal department/area names in different plants.
        result.add(plant("", "plantId", "PLANT"));
        result.add(new Link("department", type == CarlexProcessConfirmation.class ? "departmentIds" : "departmentId", PlantMasterDataItem.class, "DEPARTMENT", type == CarlexProcessConfirmation.class));
        String area = type == GembaWalkRecord.class ? "locationOfMswConducted" : type == GembaKaizenRecord.class ? "gembaKaizenLocation"
                : type == AbnormalityReportingRecord.class ? "areaMachine" : "areaOfGwProcessConfirmationConducted";
        result.add(plant(area, "processAreaId", "PROCESS_AREA"));
        if (type == GembaKaizenRecord.class) result.add(new Link("classificationOfKaizen", "classificationOfKaizenId", GembaKaizenMasterDataItem.class, "CLASSIFICATION_OF_KAIZEN", false));
        if (type == AbnormalityReportingRecord.class) {
            result.add(new Link("typeOfTag", "typeOfTagId", AbnormalityMasterDataItem.class, "ABT_TAG_TYPE", false));
            result.add(new Link("abnormalityDefectType", "abnormalityDefectTypeId", AbnormalityMasterDataItem.class, "ABNORMALITY_DEFECT_TYPE", false));
        }
        return result;
    }

    public Catalog catalog() {
        Map<Class<?>, List<Master>> result = new HashMap<>();
        for (Class<?> type : List.of(PlantMasterDataItem.class, GembaKaizenMasterDataItem.class, AbnormalityMasterDataItem.class, GembaWalkMasterDataItem.class)) {
            List<Master> items = new ArrayList<>();
            for (Object item : em.createQuery("from " + type.getSimpleName(), type).getResultList()) {
                JsonNode n = mapper.valueToTree(item);
                String parentPlant = text(n, "parentPlant"), parentDepartment = text(n, "parentDepartment");
                if (item instanceof PlantMasterDataItem plant) {
                    parentPlant = currentParentName(plant, "parentPlantId", parentPlant);
                    parentDepartment = currentParentName(plant, "parentDepartmentId", parentDepartment);
                }
                items.add(new Master(n.path("id").asLong(), text(n, "category"), text(n, "name"), parentPlant, parentDepartment));
            }
            result.put(type, items);
        }
        return new Catalog(result);
    }

    public void enrich(Object entity, ObjectNode node, Catalog catalog, List<String> warnings) {
        if (!(entity instanceof MasterMappedEntity mapped)) return;
        Class<?> type = org.springframework.util.ClassUtils.getUserClass(entity);
        for (Link link : links(type)) {
            String name = text(node, link.field());
            var saved = mapped.getMasterReferences().get(link.column());
            if (saved != null && (link.field().isEmpty() || same(name, saved.name()))) {
                node.put(link.column(), ids(saved.ids()));
                if (saved.ids().stream().anyMatch(id -> catalog.category(link).stream().noneMatch(m -> m.id().equals(id))))
                    warn(warnings, node, link.column() + " retains a historical reference to a removed master");
                continue;
            }
            List<Master> matches = matchNames(link, name, node, catalog);
            List<Long> ids = matches.stream().map(Master::id).toList();
            node.put(link.column(), ids(ids));
            if (!ids.isEmpty()) mapped.getMasterReferences().put(link.column(), new MasterMappedEntity.Reference(ids, name));
            else mapped.getMasterReferences().remove(link.column());
        }
        // Infer a plant only after a unique area/department link has been established.
        if (links(type).stream().anyMatch(l -> l.field().isEmpty()) && text(node, "plantId").isBlank()) {
            Set<String> scopes = new HashSet<>();
            for (Link link : links(type)) {
                if (!link.master().equals(PlantMasterDataItem.class) || link.field().isEmpty()) continue;
                var ref = mapped.getMasterReferences().get(link.column());
                if (ref == null) continue;
                catalog.category(link).stream().filter(m -> ref.ids().contains(m.id())).map(Master::plant).filter(p -> !p.isBlank()).forEach(scopes::add);
            }
            if (scopes.size() == 1) {
                var matches = catalog.category(plant("", "plantId", "PLANT")).stream().filter(m -> same(m.name(), scopes.iterator().next())).toList();
                if (matches.size() == 1) {
                    node.put("plantId", matches.get(0).id().toString());
                    mapped.getMasterReferences().put("plantId", new MasterMappedEntity.Reference(List.of(matches.get(0).id()), ""));
                }
            }
        }
        // A unique area can establish the plant after a same-named department was ambiguous.
        for (Link link : links(type)) {
            if (!text(node, link.column()).isBlank() || link.field().isEmpty()) continue;
            List<Master> matches = matchNames(link, text(node, link.field()), node, catalog);
            if (!matches.isEmpty()) {
                List<Long> resolved = matches.stream().map(Master::id).toList();
                node.put(link.column(), ids(resolved));
                mapped.getMasterReferences().put(link.column(), new MasterMappedEntity.Reference(resolved, text(node, link.field())));
            } else if (!text(node, link.field()).isBlank()) {
                warn(warnings, node, link.field() + " '" + text(node, link.field()) + "' has no unique scoped master match; its source value is retained");
            }
        }
        if (entity instanceof GembaWalkRecord walk && node.path("observations").isArray()) {
            for (int i = 0; i < walk.getObservations().size(); i++) enrich(walk.getObservations().get(i), (ObjectNode) node.path("observations").get(i), catalog, warnings);
        }
    }

    /** Resolve CSV IDs before normal validation. An unchanged legacy name may remain unresolved. */
    public void resolve(Class<?> type, ObjectNode original, ObjectNode incoming, Set<String> headers, Catalog catalog, List<String> warnings) {
        resolve(type, original, incoming, headers, catalog, warnings, false);
    }

    public void resolveCloud(Class<?> type, ObjectNode original, ObjectNode incoming, Set<String> headers, Catalog catalog, List<String> warnings) {
        resolve(type, original, incoming, headers, catalog, warnings, true);
    }

    private void resolve(Class<?> type, ObjectNode original, ObjectNode incoming, Set<String> headers, Catalog catalog, List<String> warnings, boolean cloud) {
        for (Link link : links(type)) {
            String oldIds = original == null ? "" : text(original, link.column());
            String oldName = original == null ? "" : text(original, link.field());
            String name = text(incoming, link.field());
            String rawIds = text(incoming, link.column());
            if (original != null && !headers.contains(link.column()) && same(name, oldName)) {
                rawIds = oldIds;
                incoming.put(link.column(), oldIds);
            }
            // Old CSVs can change a name without carrying an ID column.
            if (!headers.contains(link.column()) && !same(name, oldName)) rawIds = "";
            boolean scopeChanged = original != null && link.master() == PlantMasterDataItem.class && !link.category().equals("PLANT")
                    && (!text(original, "plantId").equals(text(incoming, "plantId")) || !text(original, "parentPlantId").equals(text(incoming, "parentPlantId"))
                    || link.category().equals("PROCESS_AREA") && (!text(original, "departmentId").equals(text(incoming, "departmentId"))
                    || !text(original, "departmentIds").equals(text(incoming, "departmentIds"))));
            boolean unchanged = original != null && same(name, oldName) && rawIds.equals(oldIds) && !scopeChanged;
            if (cloud && headers.contains(link.column()) && rawIds.isBlank() && isReportingType(type)) {
                // An explicitly blank source link must not bind to an unrelated local name match.
                incoming.put(link.column(), "");
                if (!name.isBlank()) warn(warnings, incoming, link.field() + " retains an unresolved source value: " + name);
                continue;
            }
            if (unchanged && !cloud) {
                if (!name.isBlank() && rawIds.isBlank()) warn(warnings, incoming, link.field() + " retains an unresolved historical value: " + name);
                if (!rawIds.isBlank()) for (Long id : parseIds(rawIds, link.many())) {
                    if (catalog.category(link).stream().noneMatch(m -> m.id().equals(id)))
                        warn(warnings, incoming, link.column() + " retains a historical reference to removed master " + id);
                }
                continue;
            }
            if (rawIds.isBlank()) {
                if (name.isBlank()) { incoming.put(link.column(), ""); continue; }
                List<Master> matches = matchNames(link, name, incoming, catalog);
                if (matches.isEmpty()) {
                    throw new IllegalArgumentException(link.field() + " has no unique master match. Supply " + link.column() + " and plant/department scope.");
                }
                incoming.put(link.column(), ids(matches.stream().map(Master::id).toList()));
                incoming.put(link.field(), matches.stream().map(Master::name).collect(Collectors.joining(", ")));
                continue;
            }
            List<Long> selected = parseIds(rawIds, link.many());
            List<Master> matches = new ArrayList<>();
            for (Long id : selected) {
                Master master = catalog.category(link).stream().filter(m -> m.id().equals(id)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown " + link.column() + ": " + id));
                if (!inScope(master, link, incoming, catalog)) throw new IllegalArgumentException(link.column() + " does not belong to the selected plant/department");
                matches.add(master);
            }
            String currentName = matches.stream().map(Master::name).collect(Collectors.joining(", "));
            if (!link.field().isEmpty()) {
                if (!cloud && !name.isBlank() && !same(name, currentName) && !same(name, oldName))
                    throw new IllegalArgumentException(link.field() + " conflicts with " + link.column() + ". Clear the ID to match by name, or use the selected master's name.");
                if (cloud && !name.isBlank() && !same(name, currentName))
                    warn(warnings, incoming, link.field() + " retains source text '" + name + "' for " + link.column() + "=" + rawIds + " (current master: " + currentName + ")");
                // Preserve historical text when the reference itself did not change.
                incoming.put(link.field(), cloud ? (name.isBlank() ? currentName : name)
                        : original != null && (rawIds.equals(oldIds) || oldIds.isBlank() && same(name, oldName)) ? oldName : currentName);
            }
            incoming.put(link.column(), ids(selected));
        }
        if (type == GembaWalkRecord.class && incoming.path("observations").isArray()) {
            for (JsonNode item : incoming.path("observations")) {
                ObjectNode before = null;
                if (original != null) for (JsonNode old : original.path("observations")) if (old.path("id").equals(item.path("id"))) before = (ObjectNode) old;
                Set<String> fields = new HashSet<>(); item.fieldNames().forEachRemaining(fields::add);
                resolve(GembaWalkObservation.class, before, (ObjectNode) item, fields, catalog, warnings, cloud);
            }
        }
    }

    public ObjectNode payload(Class<?> type, ObjectNode input) {
        ObjectNode result = input.deepCopy();
        links(type).forEach(link -> result.remove(link.column()));
        if (type == GembaWalkRecord.class) for (JsonNode item : result.path("observations")) links(GembaWalkObservation.class).forEach(link -> ((ObjectNode) item).remove(link.column()));
        return result;
    }

    public void store(Object entity, ObjectNode input) {
        if (!(entity instanceof MasterMappedEntity mapped)) return;
        Map<String, MasterMappedEntity.Reference> refs = new LinkedHashMap<>();
        ObjectNode saved = mapper.valueToTree(entity);
        for (Link link : links(org.springframework.util.ClassUtils.getUserClass(entity))) {
            String value = text(input, link.column());
            if (!value.isBlank()) refs.put(link.column(), new MasterMappedEntity.Reference(parseIds(value, link.many()), text(saved, link.field())));
        }
        mapped.setMasterReferences(refs);
        if (entity instanceof GembaWalkRecord walk) {
            for (int i = 0; i < walk.getObservations().size() && i < input.path("observations").size(); i++) store(walk.getObservations().get(i), (ObjectNode) input.path("observations").get(i));
        }
    }

    public ObjectNode currentMasterLabels(Class<?> type, ObjectNode input) {
        ObjectNode result = input.deepCopy();
        for (Link link : links(type)) {
            String id = text(input, link.column());
            if (id.isBlank() || link.field().isEmpty() || link.many()) continue;
            Object master = em.find(link.master(), Long.valueOf(id));
            if (master != null) result.put(link.field(), mapper.valueToTree(master).path("name").asText());
        }
        return result;
    }

    /** Apply replacements together so swapped IDs cannot be remapped twice. */
    public void remapMasterIds(Class<?> masterType, Map<Long, Long> replacements) {
        if (replacements.isEmpty()) return;
        for (Class<?> type : List.of(PlantMasterDataItem.class, AppUser.class, GembaWalkRecord.class,
                GembaWalkObservation.class, GembaKaizenRecord.class, AbnormalityReportingRecord.class, CarlexProcessConfirmation.class)) {
            List<Link> affected = links(type).stream().filter(link -> link.master() == masterType).toList();
            if (affected.isEmpty()) continue;
            for (Object item : em.createQuery("from " + type.getSimpleName(), type).getResultList()) {
                var mapped = (MasterMappedEntity) item;
                for (Link link : affected) {
                    var before = mapped.getMasterReferences().get(link.column());
                    if (before == null || before.ids().stream().noneMatch(replacements::containsKey)) continue;
                    var ids = before.ids().stream().map(id -> replacements.getOrDefault(id, id)).distinct().toList();
                    mapped.getMasterReferences().put(link.column(), new MasterMappedEntity.Reference(ids, before.name()));
                }
            }
        }
        em.flush();
    }

    /** Keep the existing hierarchy UI working when a linked plant or department is renamed. */
    public void refreshMasterHierarchy() {
        for (PlantMasterDataItem item : em.createQuery("from PlantMasterDataItem", PlantMasterDataItem.class).getResultList()) {
            String plant = currentParentName(item, "parentPlantId", Objects.toString(item.getParentPlant(), ""));
            String department = currentParentName(item, "parentDepartmentId", Objects.toString(item.getParentDepartment(), ""));
            if (!plant.isBlank()) item.setParentPlant(plant);
            if (!department.isBlank()) item.setParentDepartment(department);
            for (String key : List.of("parentPlantId", "parentDepartmentId")) {
                var reference = item.getMasterReferences().get(key);
                if (reference != null) item.getMasterReferences().put(key, new MasterMappedEntity.Reference(reference.ids(), key.equals("parentPlantId") ? plant : department));
            }
        }
    }

    private List<Master> matchNames(Link link, String name, ObjectNode row, Catalog catalog) {
        if (name.isBlank()) return List.of();
        List<Master> result = new ArrayList<>();
        for (String part : link.many() ? name.split(",") : new String[]{name}) {
            List<Master> matches = catalog.category(link).stream().filter(m -> same(m.name(), part)).filter(m -> inScope(m, link, row, catalog)).toList();
            if (matches.size() != 1) return List.of();
            result.add(matches.get(0));
        }
        return result;
    }
    private String currentParentName(PlantMasterDataItem item, String key, String fallback) {
        var reference = item.getMasterReferences().get(key);
        if (reference == null || reference.ids().size() != 1 || !same(reference.name(), fallback)) return fallback;
        PlantMasterDataItem parent = em.find(PlantMasterDataItem.class, reference.ids().get(0));
        return parent == null ? fallback : parent.getName();
    }
    private boolean inScope(Master master, Link link, ObjectNode row, Catalog catalog) {
        if (link.master() != PlantMasterDataItem.class || link.category().equals("PLANT")) return true;
        String plantName = text(row, "plant");
        if (plantName.isBlank()) plantName = text(row, "parentPlant");
        String plantId = text(row, "plantId");
        if (plantId.isBlank()) plantId = text(row, "parentPlantId");
        if (!plantId.isBlank()) {
            final String selected = plantId;
            plantName = catalog.rows().getOrDefault(PlantMasterDataItem.class, List.of()).stream().filter(m -> m.category().equals("PLANT") && m.id().toString().equals(selected)).map(Master::name).findFirst().orElse("__unknown_plant__");
        }
        if (!plantName.isBlank() && !same(master.plant(), plantName)) return false;
        String department = text(row, "department");
        if (department.isBlank()) department = text(row, "parentDepartment");
        String departmentIds = text(row, "departmentId");
        if (departmentIds.isBlank()) departmentIds = text(row, "departmentIds");
        if (departmentIds.isBlank()) departmentIds = text(row, "parentDepartmentId");
        if (link.category().equals("PROCESS_AREA") && !departmentIds.isBlank()) {
            List<Long> selected = parseIds(departmentIds, true);
            return catalog.rows().getOrDefault(PlantMasterDataItem.class, List.of()).stream()
                    .filter(m -> m.category().equals("DEPARTMENT") && selected.contains(m.id()))
                    .anyMatch(m -> same(m.name(), master.department()) && same(m.plant(), master.plant()));
        }
        return !link.category().equals("PROCESS_AREA") || department.isBlank()
                || Arrays.stream(department.split(",")).anyMatch(d -> same(d, master.department()));
    }
    private static boolean isReportingType(Class<?> type) {
        return List.of(GembaWalkRecord.class, GembaWalkObservation.class, GembaKaizenRecord.class,
                AbnormalityReportingRecord.class, CarlexProcessConfirmation.class).contains(type);
    }
    private static List<Long> parseIds(String value, boolean many) {
        try {
            List<Long> result = Arrays.stream(value.split(",", -1)).map(String::trim).map(Long::valueOf).toList();
            if ((!many && result.size() != 1) || result.stream().anyMatch(id -> id <= 0) || new HashSet<>(result).size() != result.size()) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("Master IDs must be distinct positive whole numbers"); }
    }
    private static String ids(List<Long> ids) { return ids.stream().map(String::valueOf).collect(Collectors.joining(",")); }
    private static String text(JsonNode node, String field) { return field.isEmpty() ? "" : node.path(field).asText("").trim(); }
    private static boolean same(String a, String b) { return a.trim().equalsIgnoreCase(b.trim()); }
    private static void warn(List<String> warnings, JsonNode node, String message) {
        warnings.add("Record " + node.path("id").asText("new") + ": " + message);
    }

    /** Additive backfill: capture only unique matches, leaving unresolved historical names untouched. */
    @Transactional
    public void captureBeforeChange(Class<?> masterType, String category, String name) {
        Catalog catalog = catalog();
        // A master edit must not serialize and dirty every unrelated reporting record.
        for (Class<?> type : List.of(PlantMasterDataItem.class, AppUser.class, GembaWalkRecord.class,
                GembaWalkObservation.class, GembaKaizenRecord.class, AbnormalityReportingRecord.class, CarlexProcessConfirmation.class)) {
            Set<Object> affected = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Link link : links(type)) {
                if (link.master() != masterType || !link.category().equals(category) || link.field().isEmpty()) continue;
                String condition = link.many() ? "lower(e." + link.field() + ") like :name" : "lower(trim(e." + link.field() + ")) = :name";
                var query = em.createQuery("select e from " + type.getSimpleName() + " e where " + condition, type);
                query.setParameter("name", link.many() ? "%" + name.trim().toLowerCase(Locale.ROOT) + "%" : name.trim().toLowerCase(Locale.ROOT));
                for (Object entity : query.getResultList()) {
                    ObjectNode node = mapper.valueToTree(entity);
                    String value = text(node, link.field());
                    if (Arrays.stream(link.many() ? value.split(",") : new String[]{value}).anyMatch(part -> same(part, name))) affected.add(entity);
                }
            }
            for (Object entity : affected) enrich(entity, mapper.valueToTree(entity), catalog, new ArrayList<>());
        }
    }

    @Transactional
    public void refreshReferences(Object entity) {
        enrich(entity, mapper.valueToTree(entity), catalog(), new ArrayList<>());
    }

    @Transactional
    public int backfill() {
        Catalog catalog = catalog(); int unresolved = 0;
        for (Class<?> type : List.of(PlantMasterDataItem.class, AppUser.class, GembaWalkRecord.class, GembaKaizenRecord.class, AbnormalityReportingRecord.class, CarlexProcessConfirmation.class)) {
            for (Object entity : em.createQuery("from " + type.getSimpleName(), type).getResultList()) {
                List<String> warnings = new ArrayList<>(); enrich(entity, mapper.valueToTree(entity), catalog, warnings); unresolved += warnings.size();
            }
        }
        return unresolved;
    }
}
