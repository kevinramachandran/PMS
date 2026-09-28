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

class SyncScheduleTest {
    @TempDir Path temp;
    @Test void savesScheduleAndRejectsInvalidValues() {
        var config = new SyncConfiguration();
        var repository = mock(SyncConfigurationRepository.class);
        when(repository.findAll()).thenReturn(java.util.List.of(config));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new CloudSyncService(repository, mock(SyncSecretService.class), mock(DataSyncService.class), new ObjectMapper());
        var payload = new java.util.HashMap<String, Object>();
        payload.put("cloudUrl", "https://pms.example.com");
        payload.put("cloudUsername", "sync");
        payload.put("downloadFolder", temp.resolve("download").toString());
        payload.put("processingFolder", temp.resolve("processing").toString());
        payload.put("completedFolder", temp.resolve("completed").toString());
        payload.put("failedFolder", temp.resolve("failed").toString());
        payload.put("scheduleMode", "TIMES");
        payload.put("scheduleTimes", "16:00, 08:00, 08:00, 12:00");
        payload.put("intervalMinutes", "120");
        var saved = service.save(payload);
        assertEquals("TIMES", saved.getScheduleMode());
        assertEquals("08:00,12:00,16:00", saved.getScheduleTimes());
        assertEquals(120, saved.getIntervalMinutes());
        var exposed = new org.example.controller.CloudSyncController(service).config();
        assertEquals("TIMES", exposed.get("scheduleMode"));
        assertEquals("08:00,12:00,16:00", exposed.get("scheduleTimes"));
        payload.put("scheduleMode", "INTERVAL");
        payload.put("intervalStartTime", "08:30");
        saved = service.save(payload);
        assertEquals("08:30", new org.example.controller.CloudSyncController(service).config().get("intervalStartTime"));
        assertEquals(java.time.LocalTime.of(8, 30), saved.getIntervalAnchorAt().toLocalTime());
        var anchor = saved.getIntervalAnchorAt().minusDays(3);
        saved.setIntervalAnchorAt(anchor);
        service.save(payload);
        assertEquals(anchor, saved.getIntervalAnchorAt(), "Unrelated saves must preserve the cadence across days");
        payload.put("intervalStartTime", "09:15");
        service.save(payload);
        assertEquals(java.time.LocalTime.of(9, 15), saved.getIntervalAnchorAt().toLocalTime());
        for (String invalid : java.util.List.of("", "25:00", "morning")) {
            payload.put("intervalStartTime", invalid);
            assertThrows(IllegalArgumentException.class, () -> service.save(payload));
        }
        payload.put("intervalStartTime", "08:30");
        for (String invalid : java.util.List.of("", "25:00", "08:00,", "morning")) {
            payload.put("scheduleTimes", invalid);
            assertThrows(IllegalArgumentException.class, () -> service.save(payload));
        }
        payload.put("scheduleTimes", "08:00");
        for (Object invalid : java.util.List.of("abc", 0, -1, 1441, 1.5)) {
            payload.put("intervalMinutes", invalid);
            assertThrows(IllegalArgumentException.class, () -> service.save(payload));
        }
        payload.put("intervalMinutes", 30);
        payload.put("scheduleMode", "UNKNOWN");
        assertThrows(IllegalArgumentException.class, () -> service.save(payload));
    }
    @Test void intervalScheduleUsesSavedFrequencyAndLastAttemptAcrossMidnight() {
        var config = new SyncConfiguration();
        config.setScheduleMode("INTERVAL");
        config.setIntervalMinutes(30);
        var last = java.time.LocalDateTime.of(2026, 9, 27, 23, 45);
        assertTrue(CloudSyncService.isDue(config, last));
        config.setLastRunAt(last);
        assertFalse(CloudSyncService.isDue(config, last.plusMinutes(29)));
        assertTrue(CloudSyncService.isDue(config, last.plusMinutes(30)));
        config.setIntervalMinutes(240);
        assertFalse(CloudSyncService.isDue(config, last.plusHours(2)));
        assertTrue(CloudSyncService.isDue(config, last.plusHours(4)));
    }

    @Test void anchoredIntervalsDoNotDriftAfterLateOrManualRunsAndCatchUpOnce() {
        var config = new SyncConfiguration();
        config.setScheduleMode("INTERVAL");
        config.setIntervalMinutes(120);
        var start = java.time.LocalDateTime.of(2026, 9, 28, 8, 30);
        config.setIntervalAnchorAt(start);
        assertFalse(CloudSyncService.isDue(config, start.minusSeconds(1)));
        assertTrue(CloudSyncService.isDue(config, start));
        config.setLastRunAt(start.plusMinutes(7));
        assertFalse(CloudSyncService.isDue(config, start.plusHours(2).minusSeconds(1)));
        assertTrue(CloudSyncService.isDue(config, start.plusHours(2)));
        config.setLastRunAt(start.plusHours(3)); // Manual run at 11:30 does not move 12:30.
        assertFalse(CloudSyncService.isDue(config, start.plusHours(3).plusMinutes(1)));
        assertTrue(CloudSyncService.isDue(config, start.plusHours(4)));
        var restart = start.plusDays(2).plusMinutes(10);
        assertTrue(CloudSyncService.isDue(config, restart));
        config.setLastRunAt(restart);
        assertFalse(CloudSyncService.isDue(config, restart.plusMinutes(1)));
        assertTrue(CloudSyncService.isDue(config, start.plusDays(2).plusHours(2)));
    }

    @Test void anchoredIntervalsContinueOvernightForEveryFrequency() {
        var config = new SyncConfiguration();
        config.setScheduleMode("INTERVAL");
        var start = java.time.LocalDateTime.of(2026, 9, 28, 23, 30);
        config.setIntervalAnchorAt(start);
        for (int minutes : new int[] {15, 30, 60, 120, 180, 240, 300, 360, 420, 480, 540, 600, 660, 720, 1440}) {
            config.setIntervalMinutes(minutes);
            config.setLastRunAt(start.plusMinutes(minutes * 10L).plusSeconds(45));
            var next = start.plusMinutes(minutes * 11L);
            assertFalse(CloudSyncService.isDue(config, next.minusSeconds(1)));
            assertTrue(CloudSyncService.isDue(config, next));
        }
    }

    @Test void multipleDailyTimesCatchUpOnceAndRespectManualRuns() {
        var config = new SyncConfiguration();
        config.setScheduleMode("TIMES");
        config.setScheduleTimes("08:00,12:00,16:00");
        var morning = java.time.LocalDateTime.of(2026, 9, 27, 8, 0);
        assertFalse(CloudSyncService.isDue(config, morning.minusMinutes(1)));
        assertTrue(CloudSyncService.isDue(config, morning.plusMinutes(3)));
        config.setLastRunAt(morning.plusMinutes(3));
        assertFalse(CloudSyncService.isDue(config, morning.plusHours(3)));
        assertTrue(CloudSyncService.isDue(config, morning.plusHours(4)));
        config.setLastRunAt(morning.plusHours(5));
        assertFalse(CloudSyncService.isDue(config, morning.plusHours(5).plusMinutes(1)));
        assertTrue(CloudSyncService.isDue(config, morning.plusHours(8)));
        config.setLastRunAt(morning.plusHours(10));
        assertFalse(CloudSyncService.isDue(config, morning.plusHours(11)));
        assertTrue(CloudSyncService.isDue(config, morning.plusDays(1)));
    }

    @Test void disabledScheduleDoesNotRunButManualSyncStillRuns() {
        var config = new SyncConfiguration();
        config.setScheduleMode("INTERVAL");
        config.setEnabled(false);
        var repository = mock(SyncConfigurationRepository.class);
        when(repository.findAll()).thenReturn(java.util.List.of(config));
        var service = new CloudSyncService(repository, mock(SyncSecretService.class), mock(DataSyncService.class), new ObjectMapper());
        service.scheduledSync();
        assertNull(config.getLastRunAt());
        service.runNow();
        assertNotNull(config.getLastRunAt());
        assertEquals("ERROR", config.getLastStatus()); // Existing configuration validation still runs.
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
    @Test void everyHourlyChoiceIsHonored() {
        var config = new SyncConfiguration();
        config.setScheduleMode("INTERVAL");
        var last = java.time.LocalDateTime.of(2026, 9, 27, 8, 0);
        config.setLastRunAt(last);
        for (int hours = 1; hours <= 12; hours++) {
            config.setIntervalMinutes(hours * 60);
            assertFalse(CloudSyncService.isDue(config, last.plusHours(hours).minusSeconds(1)));
            assertTrue(CloudSyncService.isDue(config, last.plusHours(hours)));
        }
    }
}
