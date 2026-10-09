package org.example.service;
import org.example.entity.SyncConfiguration;
import org.example.repository.SyncConfigurationRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CloudRoutingServiceTest {
    @Test void automaticRoutingBlocksLocalWritesButAllowsReadsAndCloudWrites() {
        var repo = mock(SyncConfigurationRepository.class);
        var config = new SyncConfiguration();
        config.setCloudUrl("https://cloud.example.test/");
        when(repo.findAll()).thenReturn(List.of(config));
        var routing = new CloudRoutingService(repo, "auto");
        assertTrue(routing.isCloudManaged());
        assertEquals("https://cloud.example.test", routing.validatedCloudUrl());
        for (String path : List.of("/api/users", "/api/users/1", "/api/dashboard-config/master-data/1", "/api/gemba-walk-config/records/1")) {
            assertTrue(routing.blocksWrite(path, "POST"));
            assertTrue(routing.blocksWrite(path, "DELETE"));
            assertFalse(routing.blocksWrite(path, "GET"));
            assertFalse(new CloudRoutingService(repo, "false").blocksWrite(path, "PUT"));
        }
        assertFalse(routing.blocksWrite("/api/auth/login", "POST"));
        config.setCloudUrl("javascript:alert(1)");
        assertThrows(IllegalArgumentException.class, routing::validatedCloudUrl);
        config.setCloudUrl("");
        assertFalse(routing.isCloudManaged());
        assertThrows(IllegalArgumentException.class, routing::validatedCloudUrl);
        assertTrue(new CloudRoutingService(repo, "true").blocksWrite("/api/users", "POST"));
    }
}
