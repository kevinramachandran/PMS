package org.example.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {GembaWalkConfigController.class, GembaKaizenConfigController.class,
        AbnormalityReportingConfigController.class, CarlexProcessConfirmationController.class,
        PlantMasterDataController.class, AbnormalityMasterDataController.class, GembaWalkMasterDataController.class,
        GembaKaizenMasterDataController.class, ProcessMasterDataController.class, CloudSyncController.class})
public class ConfigurationErrorHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> validation(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("status", "error", "message",
                exception.getMessage() == null ? "Check the supplied values and try again." : exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(Map.of("status", "error", "message",
                "The change conflicts with existing or linked data. Check for duplicate values and dependent records. No changes were saved."));
    }
}
