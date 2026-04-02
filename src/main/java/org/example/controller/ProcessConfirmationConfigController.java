package org.example.controller;

import org.example.entity.ProcessConfirmationConfig;
import org.example.service.ProcessConfirmationConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/process-confirmation")
@CrossOrigin
public class ProcessConfirmationConfigController {

    private final ProcessConfirmationConfigService service;

    public ProcessConfirmationConfigController(ProcessConfirmationConfigService service) {
        this.service = service;
    }

    @GetMapping("/latest")
    public ResponseEntity<ProcessConfirmationConfig> getLatest() {
        return service.getLatest()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/period/{periodLabel}")
    public ResponseEntity<ProcessConfirmationConfig> getByPeriod(@PathVariable String periodLabel) {
        return service.getByPeriod(periodLabel)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/periods")
    public List<String> getPeriods() {
        return service.getPeriods();
    }

    @PostMapping("/replace/date/{date}")
    public ProcessConfirmationConfig replaceByDate(
            @PathVariable String date,
            @RequestBody ProcessConfirmationConfig payload) {
        return service.replaceByDate(LocalDate.parse(date), payload);
    }
}
