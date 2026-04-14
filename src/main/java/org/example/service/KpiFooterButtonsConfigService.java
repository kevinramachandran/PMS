package org.example.service;

import org.example.entity.KpiFooterButtonsConfig;
import org.example.repository.KpiFooterButtonsConfigRepository;
import org.springframework.stereotype.Service;

@Service
public class KpiFooterButtonsConfigService {

    private static final long SINGLETON_ID = 1L;

    private final KpiFooterButtonsConfigRepository repository;

    public KpiFooterButtonsConfigService(KpiFooterButtonsConfigRepository repository) {
        this.repository = repository;
    }

    public KpiFooterButtonsConfig getOrCreateDefaults() {
        return repository.findById(SINGLETON_ID)
                .orElseGet(() -> {
                    KpiFooterButtonsConfig defaults = new KpiFooterButtonsConfig();
                    defaults.setId(SINGLETON_ID);
                    defaults.setButton1Label("Solvex");
                    defaults.setButton1Url("https://www.solvexes.com/");
                    defaults.setButton2Label("Carlsbridge");
                    defaults.setButton2Url("");
                    return repository.save(defaults);
                });
    }

    public KpiFooterButtonsConfig save(KpiFooterButtonsConfig incoming) {
        KpiFooterButtonsConfig target = getOrCreateDefaults();
        target.setButton1Label(sanitize(incoming.getButton1Label()));
        target.setButton1Url(sanitize(incoming.getButton1Url()));
        target.setButton1Type(sanitize(incoming.getButton1Type()));
        target.setButton2Label(sanitize(incoming.getButton2Label()));
        target.setButton2Url(sanitize(incoming.getButton2Url()));
        target.setButton2Type(sanitize(incoming.getButton2Type()));
        // File fields (button1File, button1FileName, etc.) are only updated via the upload endpoint
        return repository.save(target);
    }

    public KpiFooterButtonsConfig saveEntity(KpiFooterButtonsConfig entity) {
        return repository.save(entity);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
