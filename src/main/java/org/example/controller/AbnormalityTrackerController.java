package org.example.controller;

import org.example.entity.AbnormalityTrackerEntry;
import org.example.service.AbnormalityTrackerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/abnormality-tracker")
@CrossOrigin
public class AbnormalityTrackerController {

    private final AbnormalityTrackerService service;

    public AbnormalityTrackerController(AbnormalityTrackerService service) {
        this.service = service;
    }

    @GetMapping("/latest")
    public List<AbnormalityTrackerEntry> getLatest() {
        return service.getLatest();
    }

    @GetMapping("/period/{periodLabel}")
    public List<AbnormalityTrackerEntry> getByPeriod(@PathVariable String periodLabel) {
        return service.getByPeriodLabel(periodLabel);
    }

    @GetMapping("/periods")
    public List<String> getAvailablePeriods() {
        return service.getAvailablePeriods();
    }

    @PostMapping("/replace/period/{periodLabel}")
    public List<AbnormalityTrackerEntry> replaceByPeriod(
            @PathVariable String periodLabel,
            @RequestBody List<AbnormalityTrackerEntry> items) {
        return service.replaceByPeriod(periodLabel, items);
    }
}
