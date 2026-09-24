package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.example.entity.AppUser;
import org.example.entity.GembaWalkRecord;
import org.example.entity.PlantMasterDataItem;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudMasterReferenceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MasterReferenceService service = new MasterReferenceService(mock(EntityManager.class), mapper);
    private final Set<String> headers = Set.of("id", "locationOfMswConducted", "processAreaId");

    @Test void cloudUserRetainsUnresolvedAreaWithoutBindingToLocalMaster() {
        var catalog = new MasterReferenceService.Catalog(Map.of(PlantMasterDataItem.class, List.of(
                new MasterReferenceService.Master(88L, "PROCESS_AREA", "Old area", "", ""))));
        for (var sourceCatalog : List.of(catalog, new MasterReferenceService.Catalog(Map.of()))) {
            var row = mapper.createObjectNode().put("id", 5).put("area", "Old area").put("areaIds", "");
            var warnings = new ArrayList<String>();
            service.resolveCloud(AppUser.class, null, row, Set.of("id", "area", "areaIds"), sourceCatalog, warnings);
            assertEquals("Old area", row.path("area").asText());
            assertEquals("", row.path("areaIds").asText());
            assertTrue(warnings.stream().anyMatch(w -> w.contains("Old area")));
        }
    }

    @Test void userReferenceValidationRemainsStrictForExplicitIdsAndManualImports() {
        var catalog = new MasterReferenceService.Catalog(Map.of());
        var row = mapper.createObjectNode().put("id", 5).put("area", "Old area").put("areaIds", "99");
        var fields = Set.of("id", "area", "areaIds");
        assertThrows(IllegalArgumentException.class, () -> service.resolveCloud(AppUser.class, null,
                row.deepCopy(), fields, catalog, new ArrayList<>()));
        row.put("areaIds", "");
        assertThrows(IllegalArgumentException.class, () -> service.resolve(AppUser.class, null,
                row.deepCopy(), fields, catalog, new ArrayList<>()));
        row.remove("areaIds");
        var warnings = new ArrayList<String>();
        service.resolveCloud(AppUser.class, null, row, Set.of("id", "area"), catalog, warnings);
        assertEquals("", row.path("areaIds").asText());
        assertFalse(warnings.isEmpty());
    }

    @Test void blankSourceLinkDoesNotBindToLocalOnlyMaster() {
        var row = mapper.createObjectNode().put("id", 9).put("locationOfMswConducted", "Area2").put("processAreaId", "");
        var catalog = new MasterReferenceService.Catalog(Map.of(PlantMasterDataItem.class, List.of(
                new MasterReferenceService.Master(88L, "PROCESS_AREA", "Area2", "Local", "Local"))));
        service.resolveCloud(GembaWalkRecord.class, null, row, headers, catalog, new ArrayList<>());
        assertEquals("", row.path("processAreaId").asText());
    }

    @Test void sourceIdsSurviveRenamedDepartmentAndLocationButWrongHierarchyFails() {
        var catalog = new MasterReferenceService.Catalog(Map.of(PlantMasterDataItem.class, List.of(
                new MasterReferenceService.Master(1L, "PLANT", "North", "", ""),
                new MasterReferenceService.Master(2L, "DEPARTMENT", "New department", "North", ""),
                new MasterReferenceService.Master(3L, "PROCESS_AREA", "New area", "North", "New department"),
                new MasterReferenceService.Master(4L, "DEPARTMENT", "Other department", "North", ""))));
        var row = mapper.createObjectNode().put("id", 9).put("plantId", "1").put("departmentId", "2")
                .put("department", "Old department").put("locationOfMswConducted", "Old area").put("processAreaId", "3");
        var fields = new HashSet<String>(); row.fieldNames().forEachRemaining(fields::add);
        var warnings = new ArrayList<String>();
        service.resolveCloud(GembaWalkRecord.class, null, row, fields, catalog, warnings);
        assertEquals("Old area", row.path("locationOfMswConducted").asText());
        assertEquals("3", row.path("processAreaId").asText());
        assertFalse(warnings.isEmpty());
        row.put("departmentId", "4");
        assertThrows(IllegalArgumentException.class, () -> service.resolveCloud(GembaWalkRecord.class, null, row, fields, catalog, new ArrayList<>()));
    }

    @Test void existingMatchingCloudIdsAreStillValidated() {
        var row = mapper.createObjectNode().put("id", 9).put("locationOfMswConducted", "Old area").put("processAreaId", "99");
        assertThrows(IllegalArgumentException.class, () -> service.resolveCloud(GembaWalkRecord.class, row.deepCopy(), row,
                headers, new MasterReferenceService.Catalog(Map.of()), new ArrayList<>()));
    }

    @Test void reportedCsvRowResolvesWithCurrentCloudRules() throws Exception {
        String file = System.getenv("PMS_SYNC_REGRESSION_CSV");
        org.junit.jupiter.api.Assumptions.assumeTrue(file != null);
        String csv = java.nio.file.Files.readString(java.nio.file.Path.of(file));
        if (csv.startsWith("\ufeff")) csv = csv.substring(1);
        try (var parser = org.apache.commons.csv.CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .build().parse(new java.io.StringReader(csv))) {
            var record = parser.getRecords().get(2);
            var row = mapper.createObjectNode();
            for (String column : List.of("id", "department", "locationOfMswConducted", "plantId", "departmentId", "processAreaId"))
                row.put(column, record.get(column));
            assertEquals("9", row.path("id").asText());
            assertEquals("Area2", row.path("locationOfMswConducted").asText());
            assertEquals("", row.path("processAreaId").asText());
            var warnings = new ArrayList<String>();
            service.resolveCloud(GembaWalkRecord.class, null, row, new HashSet<>(parser.getHeaderNames()),
                    new MasterReferenceService.Catalog(Map.of()), warnings);
            assertEquals("Area2", row.path("locationOfMswConducted").asText());
            assertFalse(warnings.isEmpty());
        }
    }

    @Test void cloudRetainsUnresolvedLocationWithoutInventingAReference() {
        var row = mapper.createObjectNode().put("id", 9).put("locationOfMswConducted", "Old area").put("processAreaId", "");
        var warnings = new ArrayList<String>();
        service.resolveCloud(GembaWalkRecord.class, null, row, headers, new MasterReferenceService.Catalog(Map.of()), warnings);
        assertEquals("Old area", row.path("locationOfMswConducted").asText());
        assertEquals("", row.path("processAreaId").asText());
        assertTrue(warnings.get(0).contains("Old area"));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(GembaWalkRecord.class, null,
                row.deepCopy(), headers, new MasterReferenceService.Catalog(Map.of()), new ArrayList<>()));
    }

    @Test void ambiguousCloudLocationRemainsUnlinked() {
        var catalog = new MasterReferenceService.Catalog(Map.of(PlantMasterDataItem.class, List.of(
                new MasterReferenceService.Master(1L, "PROCESS_AREA", "Area", "Plant A", "Dept"),
                new MasterReferenceService.Master(2L, "PROCESS_AREA", "Area", "Plant B", "Dept"))));
        var row = mapper.createObjectNode().put("id", 9).put("locationOfMswConducted", "Area").put("processAreaId", "");
        var warnings = new ArrayList<String>();
        service.resolveCloud(GembaWalkRecord.class, null, row, headers, catalog, warnings);
        assertEquals("", row.path("processAreaId").asText());
        assertFalse(warnings.isEmpty());
    }

    @Test void unknownExplicitIdFailsButLegacyUnresolvedTextIsRetained() {
        var row = mapper.createObjectNode().put("id", 9).put("locationOfMswConducted", "Old area").put("processAreaId", "99");
        var catalog = new MasterReferenceService.Catalog(Map.of());
        assertThrows(IllegalArgumentException.class, () -> service.resolveCloud(GembaWalkRecord.class, null,
                row, headers, catalog, new ArrayList<>()));
        row.remove("processAreaId");
        var warnings = new ArrayList<String>();
        service.resolveCloud(GembaWalkRecord.class, null, row, Set.of("id", "locationOfMswConducted"), catalog, warnings);
        assertEquals("Old area", row.path("locationOfMswConducted").asText());
        assertFalse(warnings.isEmpty());
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> mappings() {
        var resolver = new MasterReferenceService(mock(EntityManager.class), new ObjectMapper());
        return java.util.stream.Stream.of(PlantMasterDataItem.class, AppUser.class, GembaWalkRecord.class,
                org.example.entity.GembaWalkObservation.class, org.example.entity.GembaKaizenRecord.class,
                org.example.entity.AbnormalityReportingRecord.class, org.example.entity.CarlexProcessConfirmation.class)
                .flatMap(type -> resolver.links(type).stream().map(link ->
                        org.junit.jupiter.params.provider.Arguments.of(type, link)));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("mappings")
    void everyMappingPreservesBlankSourceLinksAndRejectsInvalidExplicitIds(Class<?> type, MasterReferenceService.Link link) {
        var row = mapper.createObjectNode().put("id", 71).put(link.column(), "");
        if (!link.field().isEmpty()) row.put(link.field(), "Historical value");
        var fields = new HashSet<String>(); row.fieldNames().forEachRemaining(fields::add);
        var catalog = new MasterReferenceService.Catalog(Map.of(link.master(), List.of(
                new MasterReferenceService.Master(88L, link.category(), "Historical value", "", ""))));
        var warnings = new ArrayList<String>();
        service.resolveCloud(type, null, row, fields, catalog, warnings);
        assertEquals("", row.path(link.column()).asText());
        if (!link.field().isEmpty()) {
            assertEquals("Historical value", row.path(link.field()).asText());
            assertFalse(warnings.isEmpty());
            var legacy = row.deepCopy(); legacy.remove(link.column());
            service.resolveCloud(type, null, legacy, Set.of("id", link.field()),
                    new MasterReferenceService.Catalog(Map.of()), new ArrayList<>());
            assertEquals("Historical value", legacy.path(link.field()).asText());
            assertEquals("", legacy.path(link.column()).asText());
        }
        for (String invalid : List.of("999", "-1", "abc", "88,88")) {
            var invalidRow = row.deepCopy().put(link.column(), invalid);
            assertThrows(IllegalArgumentException.class, () -> service.resolveCloud(type, null,
                    invalidRow, fields, catalog, new ArrayList<>()));
        }
    }

    @Test void nestedWalkObservationRetainsUnresolvedMappings() {
        var row = mapper.createObjectNode().put("id", 71);
        row.putArray("observations").addObject().put("id", 72)
                .put("gembaCategory", "Old category").put("gembaCategoryId", "")
                .put("lifeSaverRule", "Old rule");
        var warnings = new ArrayList<String>();
        service.resolveCloud(GembaWalkRecord.class, null, row, Set.of("id", "observations"),
                new MasterReferenceService.Catalog(Map.of()), warnings);
        assertEquals("Old category", row.path("observations").get(0).path("gembaCategory").asText());
        assertEquals("", row.path("observations").get(0).path("lifeSaverRuleId").asText());
        assertEquals(2, warnings.size());
    }
}
