package org.example.config;

import org.example.entity.SyncConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class InstanceConfigurationTest {
    private StandardEnvironment environment(Map<String, Object> overrides) throws Exception {
        var properties = new Properties();
        try (var input = getClass().getResourceAsStream("/application.properties")) {
            assertNotNull(input);
            properties.load(input);
        }
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("instance", overrides));
        environment.getPropertySources().addLast(new PropertiesPropertySource("application", properties));
        return environment;
    }

    @Test void separateInstancesResolveSeparateDatabasesPortsAndCookies() throws Exception {
        var client = environment(Map.of("DB_NAME", "pms_client", "SERVER_PORT", "8081"));
        var test = environment(Map.of("DB_NAME", "pms_test", "SERVER_PORT", "8082",
                "DB_HOST", "mysql-server", "DB_PORT", "3307"));
        assertTrue(client.getRequiredProperty("spring.datasource.url").startsWith("jdbc:mysql://localhost:3306/pms_client?"));
        assertTrue(test.getRequiredProperty("spring.datasource.url").startsWith("jdbc:mysql://mysql-server:3307/pms_test?"));
        assertTrue(test.getRequiredProperty("spring.datasource.url").contains("createDatabaseIfNotExist=true"));
        assertEquals("update", test.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("8081", client.getProperty("server.port"));
        assertEquals("8082", test.getProperty("server.port"));
        assertEquals("PMS_SESSION_8081", client.getProperty("server.servlet.session.cookie.name"));
        assertEquals("PMS_SESSION_8082", test.getProperty("server.servlet.session.cookie.name"));
    }

    @Test void explicitUrlAndCookieOverridesRemainSupported() throws Exception {
        var environment = environment(Map.of("DB_URL", "jdbc:mysql://custom:3309/existing",
                "DB_NAME", "ignored", "SESSION_COOKIE_NAME", "CUSTOM_SESSION"));
        assertEquals("jdbc:mysql://custom:3309/existing", environment.getProperty("spring.datasource.url"));
        assertEquals("CUSTOM_SESSION", environment.getProperty("server.servlet.session.cookie.name"));
    }

    @Test void defaultsRemainUsableAndSyncFoldersAreInstanceRelative() throws Exception {
        var environment = environment(Map.of());
        assertEquals("165", environment.getProperty("server.port"));
        assertTrue(environment.getRequiredProperty("spring.datasource.url").contains("/brewery_pms?"));
        var sync = new SyncConfiguration();
        assertEquals("sync/download", sync.getDownloadFolder());
        assertEquals("sync/processing", sync.getProcessingFolder());
        assertEquals("sync/completed", sync.getCompletedFolder());
        assertEquals("sync/failed", sync.getFailedFolder());
    }
}
