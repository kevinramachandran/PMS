package org.example.controller;

import org.example.entity.TrainingScheduleItem;
import org.example.service.TrainingScheduleService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/training-schedule")
@CrossOrigin
public class TrainingScheduleController {

    private final TrainingScheduleService service;

    public TrainingScheduleController(TrainingScheduleService service) {
        this.service = service;
    }

    @GetMapping("/latest")
    public List<TrainingScheduleItem> getLatest() {
        return service.getLatest();
    }

    @GetMapping("/period/{periodLabel}")
    public List<TrainingScheduleItem> getByPeriod(@PathVariable String periodLabel) {
        return service.getByPeriod(periodLabel);
    }

    @GetMapping("/periods")
    public List<String> getPeriods() {
        return service.getPeriods();
    }

    @PostMapping("/replace/date/{date}")
    public List<TrainingScheduleItem> replaceByDate(
            @PathVariable String date,
            @RequestBody List<TrainingScheduleItem> rows) {
        return service.replaceByDate(LocalDate.parse(date), rows);
    }
}
