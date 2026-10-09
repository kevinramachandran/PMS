package org.example.service;

import org.example.repository.SyncConfigurationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;

@Service
public class CloudRoutingService {
    private final SyncConfigurationRepository repository;
    private final String mode;
    public CloudRoutingService(SyncConfigurationRepository repository,
            @Value("${app.cloud-managed-edits:auto}") String mode) {
        this.repository = repository;
        this.mode = mode;
    }
    public String cloudUrl() {
        String value = repository.findAll().stream().findFirst().map(config -> config.getCloudUrl()).orElse("");
        return value == null ? "" : value.trim().replaceAll("/+$", "");
    }
    public boolean isCloudManaged() {
        if ("false".equalsIgnoreCase(mode)) return false;
        if ("true".equalsIgnoreCase(mode)) return true;
        return !cloudUrl().isBlank();
    }
    public String validatedCloudUrl() {
        String value = cloudUrl();
        if (value.isBlank()) throw new IllegalArgumentException("Set the Cloud PMS URL in Cloud Sync Configuration first.");
        URI uri = URI.create(value);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Cloud PMS URL must be an HTTP or HTTPS base URL without credentials, query or fragment.");
        }
        return value;
    }
    public boolean blocksWrite(String path, String method) {
        if (java.util.Set.of("GET", "HEAD", "OPTIONS").contains(method.toUpperCase(java.util.Locale.ROOT))) return false;
        boolean managed = path.equals("/api/users") || path.startsWith("/api/users/")
                || path.startsWith("/api/dashboard-config/master-data/")
                || path.startsWith("/api/dashboard-config/abnormality-master-data/")
                || path.startsWith("/api/dashboard-config/gemba-walk-master-data/")
                || path.startsWith("/api/dashboard-config/gemba-kaizen-master-data/")
                || path.startsWith("/api/dashboard-config/process-master-data/")
                || path.startsWith("/api/gemba-kaizen-config/records")
                || path.startsWith("/api/gemba-walk-config/records")
                || path.startsWith("/api/abnormality-reporting-config/records")
                || path.startsWith("/api/carlex-process-confirmation/records");
        return managed && isCloudManaged();
    }
}
