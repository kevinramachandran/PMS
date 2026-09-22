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
    @Test void cloudSessionKeepsAllCookiesAndReportsLoginErrors() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var accepted = new java.util.concurrent.atomic.AtomicBoolean();
        var rejectLogin = new java.util.concurrent.atomic.AtomicBoolean();
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
        server.createContext("/api/data-sync/", exchange -> {
            String cookie = String.join(";", exchange.getRequestHeaders().getOrDefault("Cookie", java.util.List.of()));
            accepted.set(cookie.contains("ROUTE=node1") && cookie.contains("JSESSIONID=session1"));
            requests.add(exchange.getRequestURI().toString());
            byte[] body = new ObjectMapper().writeValueAsBytes(new DataSyncService.CsvExport(1, "id\n", java.util.List.of("Source mapping warning")));
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
            var secrets = mock(SyncSecretService.class);
            when(secrets.decrypt("encrypted")).thenReturn("password");
            var dataSync = mock(DataSyncService.class);
            when(dataSync.importCsvForSync(anyString(), anyString(), eq("id\n"))).thenReturn(Map.of());
            Files.createDirectories(Path.of(config.getDownloadFolder()));
            Files.writeString(temp.resolve("download/gemba-walk____old.csv"), "old data from failed download");
            var service = new CloudSyncService(repository, secrets, dataSync, new ObjectMapper());
            service.runNow();
            assertTrue(accepted.get());
            assertEquals("SUCCESS_WITH_WARNINGS", config.getLastStatus(), config.getLastMessage());
            assertEquals(8, requests.size());
            assertTrue(requests.get(0).contains("plant-master/export-for-sync?category=PLANT"));
            assertTrue(requests.get(7).contains("gemba-walk/export-for-sync"));
            assertTrue(config.getDatasets().contains("plant-master:PROCESS_AREA"));
            assertTrue(Files.exists(temp.resolve("download/gemba-walk____old.csv")));
            verify(dataSync, never()).importCsvForSync(anyString(), anyString(), contains("old data"));
            try (var reports = Files.list(temp.resolve("completed"))) {
                Path report = reports.filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow();
                assertTrue(Files.readString(report).contains("Source mapping warning"));
            }
            rejectLogin.set(true);
            service.runNow();
            assertEquals("ERROR", config.getLastStatus());
            assertTrue(config.getLastMessage().contains("Invalid credentials"));
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
    @Test void allFilesFollowTheSameDependencyOrderAndStopAtFirstFailure() throws Exception {
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

        assertThrows(IOException.class, () -> service.processEligible(config, true));

        var order = inOrder(dataSync);
        for (String spec : plan) {
            String[] parts = spec.split(":", -1);
            order.verify(dataSync).importCsvForSync(parts[0], parts.length > 1 ? parts[1] : "", spec);
            if (spec.equals("abnormality-master:ABNORMALITY_DEFECT_TYPE")) break;
        }
        verifyNoMoreInteractions(dataSync);
        try (var reports = Files.list(temp.resolve("completed"))) {
            var report = new ObjectMapper().readTree(reports.filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow().toFile());
            assertEquals(1, report.path("errors").size());
            assertEquals(11, report.path("skippedFiles").size());
        }
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
        IOException error = assertThrows(IOException.class, () -> service.processEligible(config, true));
        assertTrue(error.getMessage().contains("CSV row 2: Unknown id"));
        assertTrue(Files.exists(temp.resolve("failed/gemba-walk____1.csv")));
        assertTrue(Files.exists(temp.resolve("completed/plant-master__PLANT__2.csv")));
        assertFalse(Files.exists(temp.resolve("completed/gemba-walk____1.csv")));
        var order = inOrder(dataSync);
        order.verify(dataSync).importCsvForSync("plant-master", "PLANT", "good");
        order.verify(dataSync).importCsvForSync("gemba-walk", "", "bad");
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
        assertTrue(Files.exists(temp.resolve("completed/plant-master_PLANT_legacy.csv")));
        assertTrue(Files.exists(temp.resolve("completed/plant-master_DEPARTMENT_legacy.csv")));
    }
}
