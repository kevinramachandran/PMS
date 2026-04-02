package org.example.controller;

import org.example.entity.LeadershipGembaTrackerEntry;
import org.example.service.LeadershipGembaTrackerService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/leadership-gemba-tracker")
@CrossOrigin
public class LeadershipGembaTrackerController {

    private final LeadershipGembaTrackerService service;

    public LeadershipGembaTrackerController(LeadershipGembaTrackerService service) {
        this.service = service;
    }

    @GetMapping("/latest")
    public List<LeadershipGembaTrackerEntry> getLatest() {
        return service.getLatest();
    }

    @GetMapping("/period/{periodLabel}")
    public List<LeadershipGembaTrackerEntry> getByPeriod(@PathVariable String periodLabel) {
        return service.getByPeriodLabel(periodLabel);
    }

    @GetMapping("/periods")
    public List<String> getPeriods() {
        return service.getAvailablePeriods();
    }

    @PostMapping("/replace/date/{date}")
    public List<LeadershipGembaTrackerEntry> replaceByDate(
            @PathVariable String date,
            @RequestBody List<LeadershipGembaTrackerEntry> items) {
        return service.replaceByDate(LocalDate.parse(date), items);
    }
}
