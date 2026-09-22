package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.*;
import org.example.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.*;

/** Authoritative cloud writes retain source IDs without changing normal JPA identity generation. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class CloudSyncRecordWriter {
    @PersistenceContext private EntityManager em;
    private final ObjectMapper mapper;
    private final MasterReferenceService references;
    private final AuthService users;

    public CloudSyncRecordWriter(ObjectMapper mapper, MasterReferenceService references, AuthService users) {
        this.mapper = mapper; this.references = references; this.users = users;
    }

    public List<?> rows(Class<?> type, String category) {
        List<?> result = em.createQuery("from " + type.getSimpleName(), type).getResultList();
        if (type == CarlexProcessConfirmation.class) for (Object item : result) {
            CarlexProcessConfirmation record = (CarlexProcessConfirmation) item;
            record.zmObservationsJson = CarlexProcessConfirmationService.cloudObservationsJson(mapper, record, "ZM");
            record.pmObservationsJson = CarlexProcessConfirmationService.cloudObservationsJson(mapper, record, "PM");
            record.qmObservationsJson = CarlexProcessConfirmationService.cloudObservationsJson(mapper, record, "QM");
        }
        return result;
    }

    /** Reconcile scoped duplicates before writing authoritative IDs, in the file's transaction. */
    public void prepareMasterImport(Class<?> type, String category, List<ObjectNode> incoming,
                                    Set<Long> changedIds, List<String> warnings) {
        Map<Long, Long> replacements = new LinkedHashMap<>();
        Set<Long> incomingIds = new HashSet<>();
        Set<List<String>> keys = new HashSet<>();
        Set<Long> staged = new LinkedHashSet<>(changedIds);
        var catalog = references.catalog();
        for (int i = 0; i < incoming.size(); i++) {
            ObjectNode row = incoming.get(i);
            Long id = row.path("id").isNull() ? null : row.path("id").asLong();
            if (id != null) incomingIds.add(id);
            ObjectNode labels = references.currentMasterLabels(type, row);
            List<String> key = masterKey(type, category, labels);
            if (!keys.add(key)) throw new IllegalArgumentException("CSV row " + (i + 2)
                    + ": duplicate master name in the same category and parent scope: " + row.path("name").asText());
            // Let the database compare names using its own collation, then check the entire hierarchy.
            var matches = em.createQuery("from " + type.getSimpleName()
                    + " e where e.category = :category and lower(trim(e.name)) = :name", type)
                    .setParameter("category", category).setParameter("name", key.get(1)).getResultList();
            for (Object duplicate : matches) {
                ObjectNode existing = mapper.valueToTree(duplicate);
                references.enrich(duplicate, existing, catalog, new ArrayList<>());
                existing = references.currentMasterLabels(type, existing);
                if (!masterKey(type, category, existing).subList(2, key.size()).equals(key.subList(2, key.size()))) continue;
                long localId = existing.path("id").asLong();
                if (Objects.equals(localId, id)) continue;
                if (id == null) throw new IllegalArgumentException("CSV row " + (i + 2)
                        + ": master already exists in this parent scope with ID " + localId + "; supply its ID to update it");
                replacements.put(localId, id);
                staged.add(localId);
                references.captureBeforeChange(type, category, existing.path("name").asText());
                warnings.add("Merged local " + category + " ID " + localId + " into incoming ID " + id + " within the same parent scope");
            }
        }
        references.remapMasterIds(type, replacements);
        em.flush();
        // Temporary names free unique keys for swaps. A failed file rolls back every change here.
        String prefix = "__sync_" + UUID.randomUUID().toString().replace("-", "") + "_";
        for (Long id : staged) em.createQuery("update " + type.getSimpleName() + " e set e.name = :name where e.id = :id")
                .setParameter("name", prefix + id).setParameter("id", id).executeUpdate();
        em.clear();
        for (Long id : replacements.keySet()) {
            if (!incomingIds.contains(id)) em.createQuery("delete from " + type.getSimpleName() + " e where e.id = :id")
                    .setParameter("id", id).executeUpdate();
        }
        em.clear();
    }

    private static List<String> masterKey(Class<?> type, String category, ObjectNode row) {
        List<String> key = new ArrayList<>(List.of(category, normalized(row, "name")));
        if (type == PlantMasterDataItem.class) {
            for (String field : List.of("parentPlant", "parentDepartment", "parentProcessArea")) key.add(normalized(row, field));
        }
        return key;
    }

    private static String normalized(ObjectNode row, String field) {
        return row.path(field).asText("").trim().toLowerCase(Locale.ROOT);
    }

    public long save(Class<?> type, String category, ObjectNode input) {
        Long id = input.path("id").isNull() || input.path("id").isMissingNode() ? null : input.path("id").asLong();
        if (id != null && id <= 0) throw new IllegalArgumentException("Cloud IDs must be positive when supplied");
        Object previous = id == null ? null : em.find(type, id);
        ObjectNode payload = references.payload(type, input);
        if (hasField(type, "category")) {
            String normalized = Objects.toString(category, "").trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (normalized.isBlank()) throw new IllegalArgumentException("Master category is required");
            payload.put("category", normalized);
            if (payload.path("name").asText("").isBlank()) throw new IllegalArgumentException("Name is required");
        }
        Object value = mapper.convertValue(payload, type);
        if (value instanceof AppUser user) users.prepareCloudUser(user, (AppUser) previous);
        if (value instanceof GembaWalkRecord walk) {
            walk.setCreatedAt(previous instanceof GembaWalkRecord old ? old.getCreatedAt() : LocalDateTime.now());
            walk.setUpdatedAt(LocalDateTime.now());
            Set<Long> seen = new HashSet<>();
            for (GembaWalkObservation observation : walk.getObservations()) {
                Long childId = observation.getId();
                if (childId != null && (childId <= 0 || !seen.add(childId)))
                    throw new IllegalArgumentException("Cloud observation IDs must be distinct and positive when supplied");
                GembaWalkObservation old = childId == null ? null : em.find(GembaWalkObservation.class, childId);
                if (old != null && (old.getRecord() == null || !Objects.equals(old.getRecord().getId(), id)))
                    throw new IllegalArgumentException("Observation ID " + childId + " belongs to another walk");
                observation.setRecord(walk);
            }
        }
        references.store(value, input);
        em.flush();
        if (id == null) {
            // Allocate the parent identity before inserting its children with their source IDs.
            List<GembaWalkObservation> observations = value instanceof GembaWalkRecord walk
                    ? new ArrayList<>(walk.getObservations()) : List.of();
            if (value instanceof GembaWalkRecord walk) walk.setObservations(new ArrayList<>());
            em.persist(value);
            em.flush();
            id = mapper.valueToTree(value).path("id").asLong();
            em.detach(value);
            if (value instanceof GembaWalkRecord walk) walk.setObservations(observations);
        } else write(value, previous != null);
        if (value instanceof GembaWalkRecord walk) {
            em.createNativeQuery("delete from gemba_walk_observations where record_id = :id").setParameter("id", id).executeUpdate();
            for (GembaWalkObservation observation : walk.getObservations()) write(observation, false);
        }
        if (value instanceof CarlexProcessConfirmation confirmation) {
            // The CSV exposes dynamic observations as JSON, rather than database child IDs.
            em.createNativeQuery("delete from carlex_process_confirmation_observations where confirmation_id = :id")
                    .setParameter("id", id).executeUpdate();
            for (String group : List.of("ZM", "PM", "QM")) {
                String json = input.path(group.toLowerCase(Locale.ROOT) + "ObservationsJson").asText("");
                for (CarlexProcessConfirmationObservation observation : CarlexProcessConfirmationService.parseCloudObservations(mapper, group, json)) {
                    observation.setConfirmation(confirmation);
                    write(observation, false);
                }
            }
        }
        // Native ID writes must not leave stale managed entities or child collections behind.
        em.clear();
        return id;
    }

    /** Identifiers come only from entity annotations; all imported values are bound parameters. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void write(Object value, boolean update) {
        Class<?> type = value.getClass();
        var factory = em.getEntityManagerFactory().unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class);
        var mapping = (org.hibernate.persister.entity.AbstractEntityPersister) factory.getMappingMetamodel().getEntityDescriptor(type);
        String table = mapping.getTableName();
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(Transient.class)
                            || field.isAnnotationPresent(OneToMany.class)) continue;
                    field.setAccessible(true);
                    Object item = field.get(value);
                    Column annotation = field.getAnnotation(Column.class);
                    String[] mappedColumns = field.isAnnotationPresent(Id.class) ? mapping.getIdentifierColumnNames() : mapping.getPropertyColumnNames(field.getName());
                    if (mappedColumns.length != 1) throw new IllegalStateException("Unsupported cloud field: " + field.getName());
                    String column = mappedColumns[0];
                    if (field.isAnnotationPresent(ManyToOne.class)) {
                        item = item == null ? null : mapper.valueToTree(item).path("id").asLong();
                    }
                    if (annotation != null && !annotation.nullable() && item == null)
                        throw new IllegalArgumentException(field.getName() + " is required");
                    if (field.isAnnotationPresent(Convert.class)) {
                        AttributeConverter converter = (AttributeConverter) field.getAnnotation(Convert.class).converter().getDeclaredConstructor().newInstance();
                        item = converter.convertToDatabaseColumn(item);
                    }
                    // A missing child identity is allocated by the database.
                    if (field.isAnnotationPresent(Id.class) && item == null) continue;
                    values.put(column, item);
                }
            }
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException("Cannot map cloud record", ex); }
        Object id = values.remove("id");
        if (!update && id != null) values.put("id", id);
        List<String> columns = new ArrayList<>(values.keySet());
        List<String> placeholders = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) placeholders.add(":v" + i);
        String sql;
        if (update) {
            List<String> assignments = new ArrayList<>();
            for (int i = 0; i < columns.size(); i++) assignments.add("`" + columns.get(i) + "` = " + placeholders.get(i));
            sql = "update " + table + " set " + String.join(",", assignments) + " where id = :recordId";
        } else sql = "insert into " + table + " (`" + String.join("`,`", columns) + "`) values (" + String.join(",", placeholders) + ")";
        Query query = em.createNativeQuery(sql);
        for (int i = 0; i < columns.size(); i++) query.setParameter("v" + i, values.get(columns.get(i)));
        if (update) query.setParameter("recordId", id);
        query.executeUpdate();
    }

    private static boolean hasField(Class<?> type, String name) {
        try { type.getDeclaredField(name); return true; }
        catch (NoSuchFieldException ex) { return false; }
    }
}
