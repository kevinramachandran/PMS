package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.PlantMasterDataItem;
import org.example.repository.PlantMasterDataItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"})
@Import({DataSyncService.class, CloudSyncRecordWriter.class, MasterReferenceService.class, PlantMasterDataService.class, DataSyncServiceTest.JsonConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DataSyncServiceTest {
    @TestConfiguration static class JsonConfig {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); }
    }
    @Autowired DataSyncService sync;
    @Autowired PlantMasterDataService plants;
    @Autowired PlantMasterDataItemRepository repository;
    @Autowired org.example.repository.GembaWalkRecordRepository walkRepository;
    @Autowired org.example.repository.AbnormalityMasterDataItemRepository defectRepository;
    @Autowired org.example.repository.AbnormalityReportingRecordRepository abnormalityRepository;
    @Autowired MasterReferenceService references;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockBean GembaKaizenConfigService kaizen;
    @MockBean AbnormalityReportingConfigService abnormality;
    @MockBean GembaWalkConfigService walk;
    @MockBean CarlexProcessConfirmationService process;
    @MockBean GembaKaizenMasterDataService kaizenMasters;
    @MockBean AbnormalityMasterDataService abnormalityMasters;
    @MockBean GembaWalkMasterDataService walkMasters;
    @MockBean ProcessMasterDataService processMasters;
    @MockBean AuthService users;
    MockHttpSession admin;
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path syncTemp;

    @BeforeEach void setup() {
        walkRepository.deleteAll();
        abnormalityRepository.deleteAll();
        defectRepository.deleteAll();
        repository.deleteAll();
        admin = new MockHttpSession(); admin.setAttribute("username", "admin"); admin.setAttribute("role", "ADMIN");
    }

    @Test void exportsMastersAndRecordsThenImportsIntoEmptyDestinationWithOriginalIds() throws Exception {
        var plant = plants.add("PLANT", "North");
        var department = plants.add("DEPARTMENT", "Packing", "North", "");
        var area = plants.add("PROCESS_AREA", "Line 1", "North", "Packing");
        sync.importCsvForSync("gemba-walk", "", "id,department,locationOfMswConducted,plantId,departmentId,processAreaId\n"
                + "90050,Packing,Line 1," + plant.getId() + "," + department.getId() + "," + area.getId() + "\n"
                + "90051,,Area2,,,\n");
        plants.update(plant.getId(), "New North").orElseThrow();
        plants.update(department.getId(), "New Packing").orElseThrow();
        plants.update(area.getId(), "New line").orElseThrow();
        when(walk.listForUser("admin", "ADMIN")).thenAnswer(call -> walkRepository.findAll());
        var source = sync.exportForSync("gemba-walk", "", admin);
        assertTrue(source.warnings().stream().anyMatch(w -> w.contains("Area2")));
        var masterExports = new java.util.LinkedHashMap<String, String>();
        for (String category : List.of("PLANT", "DEPARTMENT", "PROCESS_AREA", "DESIGNATION"))
            masterExports.put(category, sync.exportForSync("plant-master", category, admin).csv());

        // Clearing this isolated H2 database models a destination with no source records.
        walkRepository.deleteAll();
        repository.deleteAll();
        var localOnly = plants.add("PLANT", "Destination only");
        var config = new org.example.entity.SyncConfiguration();
        config.setDownloadFolder(syncTemp.resolve("download").toString());
        config.setProcessingFolder(syncTemp.resolve("processing").toString());
        config.setCompletedFolder(syncTemp.resolve("completed").toString());
        config.setFailedFolder(syncTemp.resolve("failed").toString());
        java.nio.file.Files.createDirectories(java.nio.file.Path.of(config.getDownloadFolder()));
        for (var entry : masterExports.entrySet()) java.nio.file.Files.writeString(syncTemp.resolve("download/plant-master__" + entry.getKey() + "__1.csv"), entry.getValue());
        java.nio.file.Files.writeString(syncTemp.resolve("download/gemba-walk____1.csv"), source.csv());
        var cloud = new CloudSyncService(mock(org.example.repository.SyncConfigurationRepository.class), mock(SyncSecretService.class), sync,
                new ObjectMapper().findAndRegisterModules());
        String summary = cloud.processEligible(config, true);
        assertTrue(summary.contains("warning(s)"));
        var imported = walkRepository.findById(90050L).orElseThrow();
        assertEquals(List.of(area.getId()), imported.getMasterReferences().get("processAreaId").ids());
        assertEquals(List.of(department.getId()), imported.getMasterReferences().get("departmentId").ids());
        assertEquals(List.of(plant.getId()), imported.getMasterReferences().get("plantId").ids());
        assertEquals("Line 1", imported.getLocationOfMswConducted());
        assertEquals("New line", repository.findById(area.getId()).orElseThrow().getName());
        assertEquals("Area2", walkRepository.findById(90051L).orElseThrow().getLocationOfMswConducted());
        assertFalse(walkRepository.findById(90051L).orElseThrow().getMasterReferences().containsKey("processAreaId"));
        assertTrue(repository.existsById(localOnly.getId()));
        assertEquals(2, sync.importCsvForSync("gemba-walk", "", source.csv()).get("unchanged"));
        assertEquals(2, walkRepository.count());
    }

    @Test void exportResolvesDepartmentAfterAreaEstablishesUniquePlant() throws Exception {
        var north = plants.add("PLANT", "North"); plants.add("PLANT", "South");
        var department = plants.add("DEPARTMENT", "Packing", "North", "");
        plants.add("DEPARTMENT", "Packing", "South", "");
        var area = plants.add("PROCESS_AREA", "Unique area", "North", "Packing");
        var record = new org.example.entity.GembaWalkRecord(); record.setDepartment("Packing"); record.setLocationOfMswConducted("Unique area");
        record = walkRepository.save(record);
        when(walk.listForUser("admin", "ADMIN")).thenAnswer(call -> walkRepository.findAll());
        var exported = sync.exportForSync("gemba-walk", "", admin);
        assertTrue(exported.warnings().isEmpty());
        var refs = walkRepository.findById(record.getId()).orElseThrow().getMasterReferences();
        assertEquals(List.of(north.getId()), refs.get("plantId").ids());
        assertEquals(List.of(department.getId()), refs.get("departmentId").ids());
        assertEquals(List.of(area.getId()), refs.get("processAreaId").ids());
    }

    @Test void cloudImportsHistoricalLocationAndAcceptsEmptyDatasets() throws Exception {
        String csv = "id,locationOfMswConducted,processAreaId\n90010,Old area,\n";
        var result = sync.importCsvForSync("gemba-walk", "", csv);
        assertEquals(1, result.get("created"));
        assertFalse(((List<?>) result.get("warnings")).isEmpty());
        var saved = walkRepository.findById(90010L).orElseThrow();
        assertEquals("Old area", saved.getLocationOfMswConducted());
        assertFalse(saved.getMasterReferences().containsKey("processAreaId"));
        assertEquals(1, sync.importCsvForSync("gemba-walk", "", csv).get("unchanged"));
        assertEquals(0, sync.importCsvForSync("gemba-walk", "", "id,locationOfMswConducted,processAreaId\n").get("created"));
        assertEquals(1, walkRepository.count());
        assertThrows(IllegalArgumentException.class, () -> sync.importCsv("gemba-walk", "", "id\n", admin));
    }

    @Test void cloudUpsertsIncomingPrimaryKeyAndRetainsOtherLocalRows() throws Exception {
        var untouched = plants.add("PLANT", "Local only");
        var result = sync.importCsvForSync("plant-master", "PLANT", "id,name\n90001,Cloud plant\n");
        assertEquals(1, result.get("created"));
        assertEquals("Cloud plant", repository.findById(90001L).orElseThrow().getName());
        assertEquals("Local only", repository.findById(untouched.getId()).orElseThrow().getName());
        result = sync.importCsvForSync("plant-master", "PLANT", "id,name\n90001,Cloud replacement\n");
        assertEquals(1, result.get("updated"));
        assertEquals(2, repository.count());
        assertEquals("Cloud replacement", repository.findById(90001L).orElseThrow().getName());
        assertEquals(1, sync.importCsvForSync("plant-master", "PLANT", "id,name\n90001,Cloud replacement\n").get("unchanged"));
        assertNotEquals(90001L, plants.add("PLANT", "Created normally").getId());
    }

    @Test void cloudWalkInsertsAndReplacesNormallyReadOnlyFieldsAndChildIds() throws Exception {
        String csv = "id,managerName,observations\n90002,Cloud manager,\"[{\"\"id\"\":90003,\"\"observationDescription\"\":\"\"First\"\"}]\"\n";
        assertEquals(1, sync.importCsvForSync("gemba-walk", "", csv).get("created"));
        var walk = walkRepository.findById(90002L).orElseThrow();
        assertEquals("Cloud manager", walk.getManagerName());
        assertEquals(90003L, walk.getObservations().get(0).getId());
        assertEquals(1, sync.importCsvForSync("gemba-walk", "", csv.replace("Cloud manager", "Changed manager").replace("90003", "90004")).get("updated"));
        walk = walkRepository.findById(90002L).orElseThrow();
        assertEquals("Changed manager", walk.getManagerName());
        assertEquals(1, walk.getObservations().size());
        assertEquals(90004L, walk.getObservations().get(0).getId());
        assertEquals(1, walkRepository.count());
    }

    @Test void cloudCannotStealObservationAndRollsBackWrites() throws Exception {
        sync.importCsvForSync("gemba-walk", "", "id,observations\n90100,\"[{\"\"id\"\":90101}]\"\n");
        assertThrows(IllegalArgumentException.class, () -> sync.importCsvForSync("gemba-walk", "",
                "id,observations\n90102,[]\n90103,\"[{\"\"id\"\":90101}]\"\n"));
        assertFalse(walkRepository.existsById(90102L));
    }

    @Test void incomingIdReplacesDifferentMasterCategoryWithoutChangingTheId() throws Exception {
        sync.importCsvForSync("plant-master", "DEPARTMENT", "id,name\n1,Old department\n");
        var result = sync.importCsvForSync("plant-master", "PLANT", "id,name,parentPlant,parentDepartment,parentPlantId,parentDepartmentId\n1,Kevin Plant,,,,\n");
        assertEquals(1, result.get("updated"));
        assertFalse(((List<?>) result.get("warnings")).isEmpty());
        var plant = repository.findById(1L).orElseThrow();
        assertEquals("PLANT", plant.getCategory());
        assertEquals("Kevin Plant", plant.getName());
        assertTrue(plant.getMasterReferences().isEmpty());
        sync.importCsvForSync("plant-master", "DEPARTMENT", "id,name,parentPlant,parentPlantId\n2,Kevin Department,Kevin Plant,1\n");
        sync.importCsvForSync("plant-master", "PROCESS_AREA", "id,name,parentPlant,parentDepartment,parentPlantId,parentDepartmentId\n3,Kevin Process,Kevin Plant,Kevin Department,1,2\n");
        sync.importCsvForSync("gemba-walk", "", "id,department,locationOfMswConducted,plantId,departmentId,processAreaId\n9,Kevin Department,Kevin Process,1,2,3\n");
        assertEquals(List.of(3L), walkRepository.findById(9L).orElseThrow().getMasterReferences().get("processAreaId").ids());
        assertEquals(1, sync.importCsvForSync("plant-master", "PLANT", "id,name\n1,Kevin Plant\n").get("unchanged"));
    }

    @Test void blankAndNullCloudIdsGetGeneratedIdsForMastersAndRecords() throws Exception {
        var masters = sync.importCsvForSync("plant-master", "PLANT", "id,name\n,Generated plant\nnull,Second generated plant\n");
        assertEquals(2, masters.get("created"));
        assertEquals(2, ((List<?>) masters.get("generatedIds")).size());
        assertEquals(2, repository.count());
        var records = sync.importCsvForSync("gemba-walk", "", "id,managerName,observations\n,Generated walk,\"[{\"\"id\"\":null,\"\"status\"\":\"\"Open\"\"}]\"\n");
        assertEquals(1, records.get("created"));
        var saved = walkRepository.findAll().get(0);
        assertNotNull(saved.getId());
        assertEquals(1, saved.getObservations().size());
        assertNotNull(saved.getObservations().get(0).getId());
        assertEquals("Open", saved.getObservations().get(0).getStatus());
        var confirmation = sync.importCsvForSync("process-confirmation", "", "id,name,pmObservationsJson\n,New confirmation,[]\n");
        assertEquals(1, confirmation.get("created"));
        assertEquals(1, ((List<?>) confirmation.get("generatedIds")).size());
    }

    @Test void generatedAndSuppliedIdsInOneFileRemainSeparateRows() throws Exception {
        var result = sync.importCsvForSync("gemba-walk", "", "id,managerName\n,Generated row\n900600,Supplied row\n");
        assertEquals(2, result.get("created"));
        assertEquals(2, walkRepository.count());
        assertEquals("Supplied row", walkRepository.findById(900600L).orElseThrow().getManagerName());
        var generated = (java.util.Map<?, ?>) ((List<?>) result.get("generatedIds")).get(0);
        assertEquals(2, generated.get("csvRow"));
        assertNotEquals(900600L, generated.get("id"));
        assertEquals("Generated row", walkRepository.findById(((Number) generated.get("id")).longValue()).orElseThrow().getManagerName());
    }

    @Test void cloudReconcilesSameScopedDefectUnderIncomingIdAndRetainsLocalRecordLinks() throws Exception {
        sync.importCsvForSync("abnormality-master", "ABNORMALITY_DEFECT_TYPE", "id,name\n2,Old type\n9,Bug\n");
        sync.importCsvForSync("abnormality-master", "ABT_TAG_TYPE", "id,name\n12,Bug\n");
        sync.importCsvForSync("abnormality", "", "id,abnormalityDefectType,abnormalityDefectTypeId\n900,Bug,9\n");
        var legacy = new org.example.entity.AbnormalityReportingRecord(); legacy.setAbnormalityDefectType("Bug");
        legacy = abnormalityRepository.save(legacy);

        var result = sync.importCsvForSync("abnormality-master", "ABNORMALITY_DEFECT_TYPE", "id,name\n2,Bug\n");

        assertEquals("Bug", defectRepository.findById(2L).orElseThrow().getName());
        assertFalse(defectRepository.existsById(9L));
        assertEquals("ABT_TAG_TYPE", defectRepository.findById(12L).orElseThrow().getCategory());
        assertEquals(List.of(2L), abnormalityRepository.findById(900L).orElseThrow().getMasterReferences().get("abnormalityDefectTypeId").ids());
        assertEquals(List.of(2L), abnormalityRepository.findById(legacy.getId()).orElseThrow().getMasterReferences().get("abnormalityDefectTypeId").ids());
        assertTrue(result.get("warnings").toString().contains("Merged local"));
        assertEquals(1, sync.importCsvForSync("abnormality-master", "ABNORMALITY_DEFECT_TYPE", "id,name\n2,Bug\n").get("unchanged"));
    }

    @Test void cloudCanSwapUniqueMasterNamesWithoutLosingEitherIncomingId() throws Exception {
        sync.importCsvForSync("abnormality-master", "ABNORMALITY_DEFECT_TYPE", "id,name\n2,Bug\n9,Leak\n");
        sync.importCsvForSync("abnormality", "", "id,abnormalityDefectType,abnormalityDefectTypeId\n900,Bug,2\n901,Leak,9\n");

        sync.importCsvForSync("abnormality-master", "ABNORMALITY_DEFECT_TYPE", "id,name\n2,Leak\n9,Bug\n");

        assertEquals("Leak", defectRepository.findById(2L).orElseThrow().getName());
        assertEquals("Bug", defectRepository.findById(9L).orElseThrow().getName());
        assertEquals(List.of(9L), abnormalityRepository.findById(900L).orElseThrow().getMasterReferences().get("abnormalityDefectTypeId").ids());
        assertEquals(List.of(2L), abnormalityRepository.findById(901L).orElseThrow().getMasterReferences().get("abnormalityDefectTypeId").ids());
        assertEquals(2, defectRepository.count());
    }

    @Test void cloudKeepsEqualNamesUnderDifferentPlantsDepartmentsAndCategoriesSeparate() throws Exception {
        sync.importCsvForSync("plant-master", "PLANT", "id,name\n1,North\n4,South\n");
        sync.importCsvForSync("plant-master", "DEPARTMENT", "id,name,parentPlantId\n2,Packing,1\n5,Packing,4\n6,Brewing,1\n");
        sync.importCsvForSync("plant-master", "PROCESS_AREA", "id,name,parentPlantId,parentDepartmentId\n30,Line 1,1,2\n31,Line 1,4,5\n32,Line 1,1,6\n");
        sync.importCsvForSync("plant-master", "DESIGNATION", "id,name,parentPlantId\n50,Line 1,1\n51,Line 1,4\n");
        sync.importCsvForSync("gemba-walk", "", "id,plantId,departmentId,processAreaId\n900,1,2,30\n901,4,5,31\n902,1,6,32\n");

        sync.importCsvForSync("plant-master", "PROCESS_AREA", "id,name,parentPlantId,parentDepartmentId\n3,Line 1,1,2\n");

        assertFalse(repository.existsById(30L));
        assertEquals("North", repository.findById(3L).orElseThrow().getParentPlant());
        assertEquals("South", repository.findById(31L).orElseThrow().getParentPlant());
        assertEquals("Brewing", repository.findById(32L).orElseThrow().getParentDepartment());
        assertTrue(repository.existsById(50L)); assertTrue(repository.existsById(51L));
        assertEquals(List.of(3L), walkRepository.findById(900L).orElseThrow().getMasterReferences().get("processAreaId").ids());
        assertEquals(List.of(31L), walkRepository.findById(901L).orElseThrow().getMasterReferences().get("processAreaId").ids());
        assertEquals(List.of(32L), walkRepository.findById(902L).orElseThrow().getMasterReferences().get("processAreaId").ids());
        String exported = sync.exportCsv("plant-master", "PROCESS_AREA", admin);
        assertEquals(3, sync.importCsvForSync("plant-master", "PROCESS_AREA", exported).get("unchanged"));
        assertEquals(3, plants.list("PROCESS_AREA").size());
    }

    @Test void blankIdsWithEqualNamesInDifferentParentScopesGenerateSeparateRows() throws Exception {
        sync.importCsvForSync("plant-master", "PLANT", "id,name\n1,North\n4,South\n");
        var result = sync.importCsvForSync("plant-master", "DEPARTMENT", "id,name,parentPlantId\n,Packing,1\nnull,Packing,4\n");
        assertEquals(2, result.get("created"));
        assertEquals(2, ((List<?>) result.get("generatedIds")).size());
        var departments = plants.list("DEPARTMENT");
        assertEquals(2, departments.size());
        assertNotEquals(departments.get(0).getId(), departments.get(1).getId());
        assertNotEquals(departments.get(0).getParentPlant(), departments.get(1).getParentPlant());
    }

    @Test void failedFileRollsBackDuplicateMergeNamesAndReferenceChanges() throws Exception {
        sync.importCsvForSync("abnormality-master", "ABNORMALITY_DEFECT_TYPE", "id,name\n2,Old type\n9,Bug\n");
        sync.importCsvForSync("abnormality", "", "id,abnormalityDefectType,abnormalityDefectTypeId\n900,Bug,9\n");

        assertThrows(IllegalArgumentException.class, () -> sync.importCsvForSync("abnormality-master", "ABNORMALITY_DEFECT_TYPE", "id,name\n2,Bug\n99,\n"));

        assertEquals("Old type", defectRepository.findById(2L).orElseThrow().getName());
        assertEquals("Bug", defectRepository.findById(9L).orElseThrow().getName());
        assertFalse(defectRepository.existsById(99L));
        assertEquals(List.of(9L), abnormalityRepository.findById(900L).orElseThrow().getMasterReferences().get("abnormalityDefectTypeId").ids());
    }

    @Test void suppliedCsvBatchImportsAfterCategoryCollisionAndRepeatsWithoutDuplicates() throws Exception {
        String fixtureDirectory = System.getenv("PMS_SYNC_FIXTURE_DIR");
        org.junit.jupiter.api.Assumptions.assumeTrue(fixtureDirectory != null);
        var config = new org.example.entity.SyncConfiguration();
        config.setDownloadFolder(syncTemp.resolve("download").toString());
        config.setProcessingFolder(syncTemp.resolve("processing").toString());
        config.setCompletedFolder(syncTemp.resolve("completed").toString());
        config.setFailedFolder(syncTemp.resolve("failed").toString());
        java.nio.file.Files.createDirectories(java.nio.file.Path.of(config.getDownloadFolder()));
        sync.importCsvForSync("plant-master", "PROCESS_AREA", "id,name\n1,Old local area\n");
        sync.importCsvForSync("abnormality-master", "ABNORMALITY_DEFECT_TYPE", "id,name\n2,Old defect\n99,Bug\n");
        var cloud = new CloudSyncService(mock(org.example.repository.SyncConfigurationRepository.class), mock(SyncSecretService.class), sync,
                new ObjectMapper().findAndRegisterModules());
        for (int run = 0; run < 2; run++) {
            int copied = 0;
            try (var files = java.nio.file.Files.list(java.nio.file.Path.of(fixtureDirectory))) {
                for (var file : files.filter(p -> p.getFileName().toString().endsWith("a5a64ad1-2fe9-4ab0-a7d1-8d817eeb43cd.csv")).toList()) {
                    java.nio.file.Files.copy(file, syncTemp.resolve("download").resolve(file.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
            assertEquals(18, copied);
            String summary = cloud.processEligible(config, true);
            if (run == 1) assertTrue(summary.startsWith("0 added"), summary);
            assertEquals("PLANT", repository.findById(1L).orElseThrow().getCategory());
            assertEquals("Kevin Plant", repository.findById(1L).orElseThrow().getName());
            assertEquals(List.of(1L), repository.findById(2L).orElseThrow().getMasterReferences().get("parentPlantId").ids());
            assertEquals(List.of(2L), repository.findById(3L).orElseThrow().getMasterReferences().get("parentDepartmentId").ids());
            assertEquals("Bug", defectRepository.findById(2L).orElseThrow().getName());
            assertFalse(defectRepository.existsById(99L));
        }
    }

    @Test void cloudSupportsOtherReportingTablesAndPreservesDynamicConfirmationData() throws Exception {
        sync.importCsvForSync("abnormality", "", "id,description\n90300,Original\n");
        sync.importCsvForSync("abnormality", "", "id,description\n90300,Replacement\n");
        assertEquals("Replacement", jdbc.queryForObject("select description from abnormality_reporting_records where id=90300", String.class));
        sync.importCsvForSync("gemba-kaizen", "", "id,kaizenIdea\n90301,Original\n");
        sync.importCsvForSync("gemba-kaizen", "", "id,kaizenIdea\n90301,Replacement\n");
        assertEquals("Replacement", jdbc.queryForObject("select kaizen_idea from gemba_kaizen_records where id=90301", String.class));
        String csv = "id,name,pmObservationsJson\n90302,Cloud,\"[{\"\"description\"\":\"\"Finding\"\",\"\"status\"\":\"\"P\"\"}]\"\n";
        sync.importCsvForSync("process-confirmation", "", csv);
        assertEquals(1, sync.importCsvForSync("process-confirmation", "", csv).get("unchanged"));
        sync.importCsvForSync("process-confirmation", "", "id,name\n90302,Replacement\n");
        assertEquals("Finding", jdbc.queryForObject("select description from carlex_process_confirmation_observations where confirmation_id=90302", String.class));
        sync.importCsvForSync("process-confirmation", "", "id,pmObservationsJson\n90302,[]\n");
        assertEquals(0, jdbc.queryForObject("select count(*) from carlex_process_confirmation_observations where confirmation_id=90302", Integer.class));
    }
    @Test void exportImportRoundTripPreservesIdQuotesNewlinesAndFormulaText() throws Exception {
        var item = plants.add("PLANT", "=Plant, \"North\"\nUnit 2");
        String csv = sync.exportCsv("plant-master", "PLANT", admin);
        assertTrue(csv.contains("'=Plant"));
        var result = sync.importCsv("plant-master", "PLANT", csv, admin);
        assertEquals(1, result.get("unchanged")); assertEquals(0, result.get("created"));
        assertEquals(item.getName(), repository.findById(item.getId()).orElseThrow().getName());
        assertEquals(1, repository.count());
    }
    @Test void updatesPrimaryKeyAndCreatesBlankId() throws Exception {
        var item = plants.add("PLANT", "Old");
        var result = sync.importCsv("plant-master", "PLANT", "id,name\r\n" + item.getId() + ",Updated\r\n,New\r\n", admin);
        assertEquals(1, result.get("updated")); assertEquals(1, result.get("created"));
        assertEquals("Updated", repository.findById(item.getId()).orElseThrow().getName());
        assertEquals(2, repository.count());
    }
    @Test void rollsBackEarlierRowsWhenLaterValidationFails() {
        var item = plants.add("PLANT", "Original");
        assertThrows(IllegalArgumentException.class, () -> sync.importCsv("plant-master", "PLANT",
                "id,name\n" + item.getId() + ",Changed\n,\n", admin));
        assertEquals("Original", repository.findById(item.getId()).orElseThrow().getName());
        assertEquals(1, repository.count());
    }
    @Test void rejectsUnknownDuplicateAndCrossCategoryIds() {
        var item = plants.add("PLANT", "Original");
        for (String csv : List.of("id,name\n999999,Unknown\n", "id,name\n" + item.getId() + ",A\n" + item.getId() + ",B\n")) {
            assertThrows(IllegalArgumentException.class, () -> sync.importCsv("plant-master", "PLANT", csv, admin));
        }
        assertThrows(IllegalArgumentException.class, () -> sync.importCsv("plant-master", "DEPARTMENT", "id,name\n" + item.getId() + ",A\n", admin));
        assertEquals("Original", repository.findById(item.getId()).orElseThrow().getName());
    }
    @Test void rejectsMalformedHeadersAndRows() {
        for (String csv : List.of("name\nA", "id,name,name\n,A,B", "id,unexpected\n,A", "id,name\n,A,extra", "id,name\n"))
            assertThrows(Exception.class, () -> sync.importCsv("plant-master", "PLANT", csv, admin));
        assertEquals(0, repository.count());
    }
    @Test void enforcesViewAndEditPermissionsSeparately() throws Exception {
        var reader = new MockHttpSession(); reader.setAttribute("username", "reader"); reader.setAttribute("role", "USER");
        reader.setAttribute("viewPermissions", Set.of(org.example.util.RoleAccess.PAGE_KPI_PLANT_NAME));
        assertTrue(sync.exportCsv("plant-master", "PLANT", reader).contains("id"));
        assertThrows(ResponseStatusException.class, () -> sync.importCsv("plant-master", "PLANT", "id,name\n,A", reader));
        assertThrows(ResponseStatusException.class, () -> sync.exportCsv("users", "", reader));
        assertThrows(ResponseStatusException.class, () -> sync.exportCsv("plant-master", "PLANT", null));
    }
    @Test void userExportNeverContainsPasswordAndBlankPasswordPreservesIt() throws Exception {
        var user = new org.example.entity.AppUser(); user.setId(7L); user.setUsername("alice");
        user.setName("Alice"); user.setRole("USER"); user.setPassword("SECRET_HASH");
        when(users.getManageableUsers()).thenReturn(List.of(user));
        when(users.updateUser(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anySet(), anySet())).thenReturn(java.util.Optional.empty());
        String csv = sync.exportCsv("users", "", admin);
        assertFalse(csv.contains("SECRET_HASH"));
        sync.importCsv("users", "", "id,name\n7,Alice Changed", admin);
        verify(users).updateUser(eq(7L), eq("Alice Changed"), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), eq(""), eq("USER"), anyString(), anySet(), anySet());
    }
    @Test void formulaEscapingIsReversible() {
        for (String value : List.of("=SUM(A1)", "+cmd", "-text", "@test", "'quoted", "\tformula", "normal", ""))
            assertEquals(value, DataSyncService.unprotectCell(DataSyncService.protectCell(value)));
    }

    @Test void configExportsRoundTripDatesAndDynamicObservations() throws Exception {
        var kaizenRow = new org.example.entity.GembaKaizenRecord(); kaizenRow.setId(1L);
        kaizenRow.setGembaKaizenGenerationDate(java.time.LocalDate.of(2026, 9, 21));
        when(kaizen.listForUser("admin", "ADMIN")).thenReturn(List.of(kaizenRow));
        when(kaizen.update(eq(1L), any(), eq("admin"), eq("ADMIN"))).thenReturn(java.util.Optional.of(kaizenRow));
        assertEquals(1, sync.importCsv("gemba-kaizen", "", sync.exportCsv("gemba-kaizen", "", admin), admin).get("unchanged"));
        var abnormalityRow = new org.example.entity.AbnormalityReportingRecord(); abnormalityRow.setId(2L);
        abnormalityRow.setDateRaised(java.time.LocalDate.of(2026, 9, 21));
        when(abnormality.listForUser("admin", "ADMIN")).thenReturn(List.of(abnormalityRow));
        when(abnormality.update(eq(2L), any(), eq("admin"), eq("ADMIN"))).thenReturn(java.util.Optional.of(abnormalityRow));
        assertEquals(1, sync.importCsv("abnormality", "", sync.exportCsv("abnormality", "", admin), admin).get("unchanged"));
        var confirmation = new org.example.entity.CarlexProcessConfirmation(); confirmation.id = 3L;
        confirmation.startTime = java.time.LocalTime.of(8, 30);
        confirmation.dateOfGwProcessConfirmationConducted = java.time.LocalDate.of(2026, 9, 21);
        confirmation.pmObservationsJson = "[{\"description\":\"First, second\\nthird\",\"status\":\"P\"}]";
        when(process.listForUser("admin", "ADMIN")).thenReturn(List.of(confirmation));
        when(process.update(eq(3L), any(), eq("admin"), eq("ADMIN"))).thenReturn(java.util.Optional.of(confirmation));
        assertEquals(1, sync.importCsv("process-confirmation", "", sync.exportCsv("process-confirmation", "", admin), admin).get("unchanged"));
        verify(process, never()).update(anyLong(), any(), anyString(), anyString());
    }

    @Test void walkObservationsMatchByIdEvenWhenCsvChangesTheirOrder() throws Exception {
        var record = new org.example.entity.GembaWalkRecord(); record.setId(4L);
        var first = new org.example.entity.GembaWalkObservation(); first.setId(10L); first.setStatus("Open");
        var second = new org.example.entity.GembaWalkObservation(); second.setId(20L); second.setStatus("Open");
        record.setObservations(List.of(first, second));
        when(walk.listForUser("admin", "ADMIN")).thenReturn(List.of(record));
        when(walk.update(eq(4L), any(), eq("admin"), eq("ADMIN"))).thenReturn(java.util.Optional.of(record));
        var csv = new java.io.StringWriter();
        try (var printer = new org.apache.commons.csv.CSVPrinter(csv, org.apache.commons.csv.CSVFormat.DEFAULT)) {
            printer.printRecord("id", "observations");
            printer.printRecord("4", "[{\"id\":20,\"status\":\"Closed\"},{\"id\":10,\"status\":\"Open\"}]");
        }
        sync.importCsv("gemba-walk", "", csv.toString(), admin);
        verify(walk).update(eq(4L), argThat(v -> v.getObservations().get(0).getId() == 10L
                && v.getObservations().get(1).getStatus().equals("Closed")), eq("admin"), eq("ADMIN"));
    }

    @Test void refusesChangesToReadOnlyFieldsInsteadOfSilentlyIgnoringThem() {
        var record = new org.example.entity.GembaKaizenRecord(); record.setId(5L); record.setKaizenIdea("Original");
        when(kaizen.listForUser("admin", "ADMIN")).thenReturn(List.of(record));
        var error = assertThrows(IllegalArgumentException.class,
                () -> sync.importCsv("gemba-kaizen", "", "id,kaizenIdea\n5,Changed", admin));
        assertTrue(error.getMessage().contains("read-only"));
        verify(kaizen, never()).update(any(), any(), anyString(), anyString());
    }

    @Test void masterIdsPersistAndSurviveRenameAndRemoval() throws Exception {
        plants.add("PLANT", "North"); plants.add("DEPARTMENT", "Packing", "North", "");
        var area = plants.add("PROCESS_AREA", "Line 1", "North", "Packing");
        var record = new org.example.entity.GembaWalkRecord(); record.setDepartment("Packing"); record.setLocationOfMswConducted("Line 1");
        record = walkRepository.save(record);
        when(walk.listForUser("admin", "ADMIN")).thenAnswer(call -> walkRepository.findAll());
        String csv = sync.exportCsv("gemba-walk", "", admin);
        assertTrue(csv.contains("processAreaId"));
        assertEquals(List.of(area.getId()), walkRepository.findById(record.getId()).orElseThrow().getMasterReferences().get("processAreaId").ids());
        plants.update(area.getId(), "Renamed line").orElseThrow();
        assertEquals(1, sync.importCsv("gemba-walk", "", csv, admin).get("unchanged"));
        verify(walk, never()).update(anyLong(), any(), anyString(), anyString());
        plants.delete(area.getId());
        var result = sync.importCsv("gemba-walk", "", csv, admin);
        assertEquals(1, result.get("unchanged"));
        assertFalse(((List<?>) result.get("warnings")).isEmpty());
        assertEquals("Line 1", walkRepository.findById(record.getId()).orElseThrow().getLocationOfMswConducted());
    }

    @Test void matchesNamesWithinPlantAndRejectsAmbiguousOrCrossPlantIds() throws Exception {
        var north = plants.add("PLANT", "North"); var south = plants.add("PLANT", "South");
        plants.add("DEPARTMENT", "Packing", "North", ""); plants.add("DEPARTMENT", "Packing", "South", "");
        var northArea = plants.add("PROCESS_AREA", "Line 1", "North", "Packing");
        var southArea = plants.add("PROCESS_AREA", "Line 1", "South", "Packing");
        when(walk.create(any(), anyString(), anyString())).thenAnswer(call -> call.getArgument(0));
        assertThrows(IllegalArgumentException.class, () -> sync.importCsv("gemba-walk", "", "id,department,locationOfMswConducted\n,Packing,Line 1", admin));
        sync.importCsv("gemba-walk", "", "id,plantId,department,locationOfMswConducted\n," + north.getId() + ",Packing,Line 1", admin);
        verify(walk).create(argThat(r -> r.getLocationOfMswConducted().equals("Line 1")), eq("admin"), eq("ADMIN"));
        assertThrows(IllegalArgumentException.class, () -> sync.importCsv("gemba-walk", "", "id,plantId,processAreaId\n," + north.getId() + "," + southArea.getId(), admin));
        assertNotEquals(northArea.getId(), southArea.getId());
    }

    @Test void newRowsResolveMasterIdWithoutNameAndRejectInvalidIds() throws Exception {
        plants.add("PLANT", "North"); plants.add("DEPARTMENT", "Packing", "North", "");
        var area = plants.add("PROCESS_AREA", "Line 1", "North", "Packing");
        when(walk.create(any(), anyString(), anyString())).thenAnswer(call -> call.getArgument(0));
        sync.importCsv("gemba-walk", "", "id,processAreaId\n," + area.getId(), admin);
        verify(walk).create(argThat(r -> r.getLocationOfMswConducted().equals("Line 1")), eq("admin"), eq("ADMIN"));
        assertThrows(IllegalArgumentException.class, () -> sync.importCsv("gemba-walk", "", "id,processAreaId\n,999999", admin));
        assertThrows(IllegalArgumentException.class, () -> sync.importCsv("gemba-walk", "", "id,processAreaId,locationOfMswConducted\n," + area.getId() + ",Wrong name", admin));
    }

    @Test void backfillLeavesAmbiguousHistoricalNamesUnmapped() {
        plants.add("PLANT", "North"); plants.add("PLANT", "South");
        plants.add("DEPARTMENT", "Packing", "North", ""); plants.add("DEPARTMENT", "Packing", "South", "");
        plants.add("PROCESS_AREA", "Line 1", "North", "Packing"); plants.add("PROCESS_AREA", "Line 1", "South", "Packing");
        var record = new org.example.entity.GembaWalkRecord(); record.setDepartment("Packing"); record.setLocationOfMswConducted("Line 1");
        record = walkRepository.save(record);
        assertTrue(references.backfill() >= 2);
        assertFalse(walkRepository.findById(record.getId()).orElseThrow().getMasterReferences().containsKey("processAreaId"));
    }

    @Test void masterRenameAndDeleteDoNotRewriteUnrelatedReportingRecords() {
        plants.add("PLANT", "North"); plants.add("DEPARTMENT", "Packing", "North", "");
        var area = plants.add("PROCESS_AREA", "Line 1", "North", "Packing");
        var unrelated = new org.example.entity.GembaWalkRecord(); unrelated.setLocationOfMswConducted("Legacy area");
        unrelated = walkRepository.save(unrelated);
        var timestamp = walkRepository.findById(unrelated.getId()).orElseThrow().getUpdatedAt();
        plants.update(area.getId(), "Renamed line").orElseThrow();
        plants.delete(area.getId());
        var retained = walkRepository.findById(unrelated.getId()).orElseThrow();
        assertEquals(timestamp, retained.getUpdatedAt());
        assertTrue(retained.getMasterReferences().isEmpty());
    }

    @Test void movingAreaRevalidatesDepartmentAndRebindsSameNamedParents() {
        plants.add("PLANT", "North"); var south = plants.add("PLANT", "South");
        plants.add("DEPARTMENT", "Packing", "North", "");
        var area = plants.add("PROCESS_AREA", "Line 1", "North", "Packing");
        assertThrows(IllegalArgumentException.class, () -> plants.update(area.getId(), "Line 1", "South", "Packing"));
        assertEquals("North", repository.findById(area.getId()).orElseThrow().getParentPlant());
        var southDepartment = plants.add("DEPARTMENT", "Packing", "South", "");
        plants.update(area.getId(), "Line 1", "South", "Packing").orElseThrow();
        var saved = repository.findById(area.getId()).orElseThrow();
        assertEquals(List.of(south.getId()), saved.getMasterReferences().get("parentPlantId").ids());
        assertEquals(List.of(southDepartment.getId()), saved.getMasterReferences().get("parentDepartmentId").ids());
    }

    @Test void parentRenameKeepsHierarchyAndDeleteConstraintsConsistent() {
        var plant = plants.add("PLANT", "North");
        var department = plants.add("DEPARTMENT", "Packing", "North", "");
        var area = plants.add("PROCESS_AREA", "Line 1", "North", "Packing");
        plants.update(plant.getId(), "New North").orElseThrow();
        plants.update(department.getId(), "New Packing").orElseThrow();
        var saved = repository.findById(area.getId()).orElseThrow();
        assertEquals("New North", saved.getParentPlant()); assertEquals("New Packing", saved.getParentDepartment());
        assertThrows(IllegalArgumentException.class, () -> plants.delete(plant.getId()));
        assertThrows(IllegalArgumentException.class, () -> plants.delete(department.getId()));
        assertTrue(plants.delete(area.getId())); assertTrue(plants.delete(department.getId())); assertTrue(plants.delete(plant.getId()));
    }

    @Test void cloudImportRollsBackEarlierRowsWhenLaterRowFails() {
        var item = plants.add("PLANT", "Original");
        assertThrows(IllegalArgumentException.class, () -> sync.importCsvForSync("plant-master", "PLANT",
                "id,name\n" + item.getId() + ",Changed\n,\n"));
        assertEquals("Original", repository.findById(item.getId()).orElseThrow().getName());
    }
}
