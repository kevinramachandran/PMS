package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.SyncConfiguration;
import org.example.repository.SyncConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudSyncServiceTest {
    @TempDir Path temp;
    @Test void largePushIsSplitWithoutDroppingRecordsAndFindsAllImageFields() throws Exception {
        var mapper = new ObjectMapper();
        var service = new CloudSyncService(mock(SyncConfigurationRepository.class), mock(SyncSecretService.class), mock(DataSyncService.class), mapper);
        var rows = mapper.createArrayNode();
        for (int i = 1; i <= 5001; i++) rows.addObject().put("id", i);
        var batches = service.pushBatches(rows);
        assertEquals(2, batches.size());
        assertEquals(5000, batches.get(0).size());
        assertEquals(5001, batches.get(1).get(0).path("id").asInt());
        var record = mapper.createObjectNode().put("pictureImage", "a.png").put("zm1ObservationImage", "b.jpg")
                .put("pmObservationsJson", "[{\"observationImage\":\"c.png\"}]");
        record.putArray("observations").addObject().put("pictureImage", "d.jpeg");
        assertEquals(java.util.Set.of("a.png", "b.jpg", "c.png", "d.jpeg"), CloudSyncService.referencedAttachmentNames(record));
    }

    @Test void badDatasetDoesNotPreventLaterDatasetsFromImporting() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/login", exchange -> {
            exchange.getRequestBody().readAllBytes(); exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=session; Path=/");
            byte[] body = "{\"status\":\"success\"}".getBytes(); exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/api/cloud-sync/data/", exchange -> {
            String dataset = exchange.getRequestURI().getPath().substring("/api/cloud-sync/data/".length());
            if (dataset.equals("gemba-walk")) { exchange.sendResponseHeaders(503, -1); exchange.close(); return; }
            byte[] body = new ObjectMapper().writeValueAsBytes(new DataSyncService.JsonSyncExport(1, dataset, "",
                    new ObjectMapper().createArrayNode(), java.util.List.of()));
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var config = new SyncConfiguration(); config.setCloudUrl("http://127.0.0.1:" + server.getAddress().getPort());
            config.setCloudUsername("sync"); config.setEncryptedCloudPassword("encrypted"); config.setDatasets("gemba-walk\ngemba-kaizen");
            config.setDownloadFolder(temp.resolve("later-download").toString()); config.setProcessingFolder(temp.resolve("later-processing").toString());
            config.setFailedFolder(temp.resolve("later-failed").toString());
            var repository = mock(SyncConfigurationRepository.class); when(repository.findAll()).thenReturn(java.util.List.of(config));
            when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
            var secrets = mock(SyncSecretService.class); when(secrets.decrypt("encrypted")).thenReturn("password");
            var sync = mock(DataSyncService.class); when(sync.applyJsonSnapshots(anyList(), isNull(), eq(false))).thenAnswer(call -> {
                java.util.List<DataSyncService.JsonSyncExport> snapshots = call.getArgument(0);
                assertTrue(snapshots.stream().anyMatch(snapshot -> snapshot.dataset().equals("gemba-kaizen")));
                assertFalse(snapshots.stream().anyMatch(snapshot -> snapshot.dataset().equals("gemba-walk")));
                return Map.of("created", 0, "updated", 0, "unchanged", 0, "successfulSnapshots", snapshots);
            });
            var service = new CloudSyncService(repository, secrets, sync, new ObjectMapper());
            service.runNow();
            assertEquals("SUCCESS", config.getLastStatus());
            assertTrue(config.getLastMessage().contains("need attention"));
            verify(sync).applyJsonSnapshots(anyList(), isNull(), eq(false));
        } finally { server.stop(0); }
    }
    @Test void cloudSessionKeepsAllCookiesAndReportsLoginErrors() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var accepted = new java.util.concurrent.atomic.AtomicBoolean();
        var rejectLogin = new java.util.concurrent.atomic.AtomicBoolean();
        var includeImage = new java.util.concurrent.atomic.AtomicBoolean();
        var missingImage = new java.util.concurrent.atomic.AtomicBoolean();
        byte[] imageBytes = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        var imageDownloads = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/api/cloud-sync/attachments/gemba-walk/photo.png/metadata", exchange -> {
            byte[] body;
            try { body = missingImage.get() ? "{}".getBytes() : new ObjectMapper().writeValueAsBytes(Map.of("checksum",
                    java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(imageBytes)), "size", imageBytes.length)); }
            catch (Exception ex) { throw new java.io.IOException(ex); }
            exchange.sendResponseHeaders(missingImage.get() ? 404 : 200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/api/cloud-sync/attachments/gemba-walk/photo.png", exchange -> {
            imageDownloads.incrementAndGet(); exchange.sendResponseHeaders(200, imageBytes.length);
            exchange.getResponseBody().write(imageBytes); exchange.close();
        });
        server.createContext("/api/auth/login", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Set-Cookie", "ROUTE=node1; Path=/");
            exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=session1; Path=/");
            byte[] body = (rejectLogin.get() ? "{\"status\":\"error\",\"message\":\"Invalid credentials\"}"
                    : "{\"status\":\"success\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        var requests = new java.util.ArrayList<String>();
        server.createContext("/api/cloud-sync/data/", exchange -> {
            String cookie = String.join(";", exchange.getRequestHeaders().getOrDefault("Cookie", java.util.List.of()));
            accepted.set(cookie.contains("ROUTE=node1") && cookie.contains("JSESSIONID=session1"));
            requests.add(exchange.getRequestURI().toString());
            String dataset = exchange.getRequestURI().getPath().substring("/api/cloud-sync/data/".length());
            String category = java.net.URLDecoder.decode(exchange.getRequestURI().getRawQuery().substring("category=".length()), java.nio.charset.StandardCharsets.UTF_8);
            var records = new ObjectMapper().createArrayNode();
            if (includeImage.get() && dataset.equals("gemba-walk"))
                records.addObject().put("id", 1).putArray("observations").addObject().put("pictureImage", "photo.png");
            byte[] body = new ObjectMapper().writeValueAsBytes(new DataSyncService.JsonSyncExport(1, dataset, category,
                    records, java.util.List.of("Source mapping warning")));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(accepted.get() ? 200 : 401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var config = new SyncConfiguration();
            config.setCloudUrl("http://127.0.0.1:" + server.getAddress().getPort());
            config.setCloudUsername("sync-user"); config.setEncryptedCloudPassword("encrypted");
            config.setDatasets("gemba-walk");
            config.setDownloadFolder(temp.resolve("download").toString());
            config.setProcessingFolder(temp.resolve("processing").toString());
            config.setCompletedFolder(temp.resolve("completed").toString());
            config.setFailedFolder(temp.resolve("failed").toString());
            var repository = mock(SyncConfigurationRepository.class);
            when(repository.findAll()).thenReturn(java.util.List.of(config));
            when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            var secrets = mock(SyncSecretService.class);
            when(secrets.decrypt("encrypted")).thenReturn("password");
            var dataSync = mock(DataSyncService.class);
            when(dataSync.applyJsonSnapshots(anyList(), isNull(), eq(false))).thenAnswer(call ->
                    Map.of("successfulSnapshots", call.getArgument(0)));
            Files.createDirectories(Path.of(config.getDownloadFolder()));
            Files.writeString(temp.resolve("download/gemba-walk____old.csv"), "old data from failed download");
            var service = new CloudSyncService(repository, secrets, dataSync, new ObjectMapper());
            var storage = new AttachmentStorageService();
            org.springframework.test.util.ReflectionTestUtils.setField(storage, "uploadDir", temp.resolve("uploads").toString());
            org.springframework.test.util.ReflectionTestUtils.setField(service, "attachmentStorage", storage);
            service.runNow();
            assertTrue(accepted.get());
            assertEquals("SUCCESS", config.getLastStatus(), config.getLastMessage());
            assertEquals(8, requests.size());
            assertTrue(requests.get(0).contains("plant-master?category=PLANT"));
            assertTrue(requests.get(7).contains("gemba-walk?category="));
            assertTrue(config.getDatasets().contains("plant-master:PROCESS_AREA"));
            assertTrue(Files.exists(temp.resolve("download/gemba-walk____old.csv")));
            verify(dataSync, never()).importCsvForSync(anyString(), anyString(), anyString());
            verify(dataSync).applyJsonSnapshots(argThat(snapshots -> snapshots.size() == 8
                    && snapshots.get(6).dataset().equals("users")), isNull(), eq(false));
            clearInvocations(dataSync);
            includeImage.set(true);
            missingImage.set(true);
            service.runNow();
            assertEquals("SUCCESS", config.getLastStatus(), config.getLastMessage());
            verify(dataSync).applyJsonSnapshots(anyList(), isNull(), eq(false));
            missingImage.set(false);
            service.runNow();
            assertEquals("SUCCESS", config.getLastStatus(), config.getLastMessage());
            assertTrue(storage.hasImage("gemba-walk", "photo.png"));
            assertEquals(1, imageDownloads.get());
            service.runNow();
            assertEquals(1, imageDownloads.get(), "unchanged attachment must skip the binary download");
            verify(dataSync, times(3)).applyJsonSnapshots(anyList(), isNull(), eq(false));
            rejectLogin.set(true);
            service.runNow();
            assertEquals("ERROR", config.getLastStatus());
            assertTrue(config.getLastMessage().contains("could not be completed"));
        } finally { server.stop(0); }
    }
    @Test void dailyScheduleCatchesMissedMinuteAndDoesNotRepeatAnAttempt() {
        var config = new SyncConfiguration();
        var now = java.time.LocalDateTime.of(2026, 9, 22, 2, 3);
        assertTrue(CloudSyncService.isDue(config, now));
        config.setLastRunAt(now);
        assertFalse(CloudSyncService.isDue(config, now.plusMinutes(1)));
        assertFalse(CloudSyncService.isDue(config, now.plusDays(1).withHour(1)));
        assertTrue(CloudSyncService.isDue(config, now.plusDays(1)));
    }
    @Test void legacyFilesFollowDependencyOrderAndContinueAfterFailure() throws Exception {
        var dataSync = mock(DataSyncService.class);
        when(dataSync.importCsvForSync(anyString(), anyString(), anyString())).thenReturn(Map.of());
        when(dataSync.importCsvForSync(eq("abnormality-master"), eq("ABNORMALITY_DEFECT_TYPE"), anyString()))
                .thenThrow(new IllegalArgumentException("invalid source row"));
        var service = new CloudSyncService(mock(SyncConfigurationRepository.class), mock(SyncSecretService.class), dataSync, new ObjectMapper());
        var config = new SyncConfiguration();
        config.setDownloadFolder(temp.resolve("download").toString()); config.setProcessingFolder(temp.resolve("processing").toString());
        config.setCompletedFolder(temp.resolve("completed").toString()); config.setFailedFolder(temp.resolve("failed").toString());
        Files.createDirectories(Path.of(config.getDownloadFolder()));
        var plan = SyncDatasetPlan.resolve("gemba-walk\ngemba-kaizen\nabnormality\nprocess-confirmation");
        for (String spec : plan) {
            String[] parts = spec.split(":", -1);
            Files.writeString(temp.resolve("download/" + parts[0] + "__" + (parts.length > 1 ? parts[1] : "") + "__run.csv"), spec);
        }

        String result = service.processEligible(config, true);
        assertTrue(result.contains("warning(s)"));

        var order = inOrder(dataSync);
        for (String spec : plan) {
            String[] parts = spec.split(":", -1);
            if (parts[0].equals("users")) order.verify(dataSync).replaceUsersForCloudSync(spec);
            else order.verify(dataSync).importCsvForSync(parts[0], parts.length > 1 ? parts[1] : "", spec);
        }
        verify(dataSync, atLeastOnce()).importCsvForSync(anyString(), anyString(), anyString());
    }
    @Test void failedImportIsReportedAndFilesAreSeparated() throws Exception {
        var dataSync = mock(DataSyncService.class);
        when(dataSync.importCsvForSync("gemba-walk", "", "bad")).thenThrow(new IllegalArgumentException("CSV row 2: Unknown id"));
        when(dataSync.importCsvForSync("plant-master", "PLANT", "good")).thenReturn(Map.of("status", "success"));
        var service = new CloudSyncService(mock(SyncConfigurationRepository.class), mock(SyncSecretService.class), dataSync, new ObjectMapper());
        var config = new SyncConfiguration();
        config.setDownloadFolder(temp.resolve("download").toString()); config.setProcessingFolder(temp.resolve("processing").toString());
        config.setCompletedFolder(temp.resolve("completed").toString()); config.setFailedFolder(temp.resolve("failed").toString());
        Files.createDirectories(Path.of(config.getDownloadFolder()));
        Files.writeString(temp.resolve("download/gemba-walk____1.csv"), "bad");
        Files.writeString(temp.resolve("download/plant-master__PLANT__2.csv"), "good");
        String message = service.processEligible(config, true);
        assertTrue(message.contains("warning(s)"));
        assertTrue(Files.exists(temp.resolve("failed/gemba-walk____1.csv")));
        assertFalse(Files.exists(temp.resolve("failed/plant-master__PLANT__2.csv")));
        assertFalse(Files.exists(temp.resolve("completed/gemba-walk____1.csv")));
        verify(dataSync).importCsvForSync("plant-master", "PLANT", "good");
        verify(dataSync).importCsvForSync("gemba-walk", "", "bad");
    }

    @Test void legacySingleUnderscoreFileNamesUseCorrectPhaseOrderingAndCategoryParsing() throws Exception {
        var dataSync = mock(DataSyncService.class);
        when(dataSync.importCsvForSync("plant-master", "PLANT", "plant")).thenReturn(Map.of("status", "success"));
        when(dataSync.importCsvForSync("plant-master", "DEPARTMENT", "dept")).thenReturn(Map.of("status", "success"));
        var service = new CloudSyncService(mock(SyncConfigurationRepository.class), mock(SyncSecretService.class), dataSync, new ObjectMapper());
        var config = new SyncConfiguration();
        config.setDownloadFolder(temp.resolve("download").toString()); config.setProcessingFolder(temp.resolve("processing").toString());
        config.setCompletedFolder(temp.resolve("completed").toString()); config.setFailedFolder(temp.resolve("failed").toString());
        Files.createDirectories(Path.of(config.getDownloadFolder()));
        Files.writeString(temp.resolve("download/plant-master_DEPARTMENT_legacy.csv"), "dept");
        Files.writeString(temp.resolve("download/plant-master_PLANT_legacy.csv"), "plant");

        service.processEligible(config, true);

        verify(dataSync).importCsvForSync("plant-master", "PLANT", "plant");
        verify(dataSync).importCsvForSync("plant-master", "DEPARTMENT", "dept");
        assertFalse(Files.exists(temp.resolve("processing/plant-master_PLANT_legacy.csv")));
        assertFalse(Files.exists(temp.resolve("processing/plant-master_DEPARTMENT_legacy.csv")));
    }

    @Test void successfulCloudSnapshotsDeleteMissingRowsAfterEveryImportInReverseDependencyOrder() throws Exception {
        var dataSync = mock(DataSyncService.class);
        when(dataSync.importCsvForSync(anyString(), anyString(), anyString())).thenReturn(Map.of());
        doNothing().when(dataSync).replaceUsersForCloudSync(anyString());
        var service = new CloudSyncService(mock(SyncConfigurationRepository.class), mock(SyncSecretService.class), dataSync, new ObjectMapper());
        var config = new SyncConfiguration();
        config.setDownloadFolder(temp.resolve("download").toString()); config.setProcessingFolder(temp.resolve("processing").toString());
        config.setCompletedFolder(temp.resolve("completed").toString()); config.setFailedFolder(temp.resolve("failed").toString());
        Files.createDirectories(Path.of(config.getDownloadFolder()));
        Files.writeString(temp.resolve("download/plant-master__PLANT__run.csv"), "id\n1\n");
        Files.writeString(temp.resolve("download/users____run.csv"), "id\n2\n");
        Files.writeString(temp.resolve("download/gemba-walk____run.csv"), "id\n3\n");

        String message = service.processEligible(config, true);

        assertTrue(message.contains("processed 3 file(s)"));
        var order = inOrder(dataSync);
        order.verify(dataSync).importCsvForSync("plant-master", "PLANT", "id\n1\n");
        order.verify(dataSync).replaceUsersForCloudSync("id\n2\n");
        order.verify(dataSync).importCsvForSync("gemba-walk", "", "id\n3\n");
        order.verify(dataSync).deleteMissingForSync("gemba-walk", "", "id\n3\n");
        order.verify(dataSync).deleteMissingForSync("plant-master", "PLANT", "id\n1\n");
    }
}
