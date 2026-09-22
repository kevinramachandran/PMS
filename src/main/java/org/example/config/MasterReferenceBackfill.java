package org.example.config;

import org.example.service.MasterReferenceService;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1000)
public class MasterReferenceBackfill implements ApplicationRunner {
    private final MasterReferenceService references;
    public MasterReferenceBackfill(MasterReferenceService references) { this.references = references; }
    @Override public void run(ApplicationArguments args) {
        int unresolved = references.backfill();
        LoggerFactory.getLogger(getClass()).info("Master reference backfill completed; {} historical values require manual mapping", unresolved);
    }
}
