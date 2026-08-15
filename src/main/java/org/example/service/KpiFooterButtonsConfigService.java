package org.example.service;

import org.example.entity.KpiFooterButtonsConfig;
import org.example.repository.KpiFooterButtonsConfigRepository;
import org.springframework.stereotype.Service;

@Service
public class KpiFooterButtonsConfigService {

    private static final long SINGLETON_ID = 1L;
    public static final String DEFAULT_PLANT_NAME = "";

    private final KpiFooterButtonsConfigRepository repository;

    public KpiFooterButtonsConfigService(KpiFooterButtonsConfigRepository repository) {
        this.repository = repository;
    }

    public KpiFooterButtonsConfig getOrCreateDefaults() {
        return repository.findById(SINGLETON_ID)
                .orElseGet(this::newDefaultConfig);
    }

    public KpiFooterButtonsConfig save(KpiFooterButtonsConfig incoming) {
        KpiFooterButtonsConfig target = getOrCreateDefaults();
        target.setButton1Label(sanitize(incoming.getButton1Label()));
        target.setButton1Url(sanitize(incoming.getButton1Url()));
        target.setButton1Type(sanitize(incoming.getButton1Type()));
        target.setButton2Label(sanitize(incoming.getButton2Label()));
        target.setButton2Url(sanitize(incoming.getButton2Url()));
        target.setButton2Type(sanitize(incoming.getButton2Type()));
        target.setButtonsJson(sanitize(incoming.getButtonsJson()));
        // File fields (button1File, button1FileName, etc.) are only updated via the upload endpoint
        return repository.save(target);
    }

    public KpiFooterButtonsConfig saveEntity(KpiFooterButtonsConfig entity) {
        return repository.save(entity);
    }

    public String getPlantName() {
        String plantName = getOrCreateDefaults().getPlantName();
        return plantName == null || plantName.isBlank() ? DEFAULT_PLANT_NAME : plantName.trim();
    }

    public KpiFooterButtonsConfig savePlantName(String plantName) {
        KpiFooterButtonsConfig target = getOrCreateDefaults();
        String sanitized = sanitize(plantName);
        target.setPlantName(sanitized);
        return repository.save(target);
    }

    private KpiFooterButtonsConfig newDefaultConfig() {
        KpiFooterButtonsConfig defaults = new KpiFooterButtonsConfig();
        defaults.setId(SINGLETON_ID);
        defaults.setButton1Label("");
        defaults.setButton1Url("");
        defaults.setButton1Type("link");
        defaults.setButton2Label("");
        defaults.setButton2Url("");
        defaults.setButton2Type("link");
        defaults.setPlantName(DEFAULT_PLANT_NAME);
        return defaults;
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
