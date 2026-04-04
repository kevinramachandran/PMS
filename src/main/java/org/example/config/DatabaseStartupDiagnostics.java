package org.example.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DatabaseStartupDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(DatabaseStartupDiagnostics.class);
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password=)[^&;]*");

    @Bean
    ApplicationRunner databaseStartupLogger(DataSource dataSource,
                                            Environment environment,
                                            @Value("${spring.datasource.url:}") String datasourceUrl) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) {
                String[] activeProfiles = environment.getActiveProfiles();
                String renderedProfiles = activeProfiles.length == 0
                    ? "default"
                    : Arrays.toString(activeProfiles);

                log.info("Active profile(s): {}", renderedProfiles);
                log.info("Configured datasource URL: {}", sanitizeUrl(datasourceUrl));

                try (Connection ignored = dataSource.getConnection()) {
                    log.info("Database connectivity check passed.");
                } catch (SQLException ex) {
                    String message = "Unable to connect to MySQL with current configuration. "
                        + "Override values using --spring.datasource.url/username/password or set DB_URL/DB_USERNAME/DB_PASSWORD.";
                    log.error(message, ex);
                    throw new IllegalStateException(message, ex);
                }
            }
        };
    }

    @Bean
    ApplicationListener<ApplicationFailedEvent> databaseFailureListener() {
        return event -> {
            if (containsAccessDenied(event.getException())) {
                log.error("MySQL access denied during startup. Verify MySQL grants and credentials. "
                    + "Expected options: application.properties defaults, environment variables, or --spring.datasource.* command-line overrides.");
            }
        };
    }

    private static boolean containsAccessDenied(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("access denied for user")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String sanitizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "not configured";
        }
        return PASSWORD_PATTERN.matcher(rawUrl).replaceAll("$1****");
    }
}