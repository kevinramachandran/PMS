package org.example.controller;

import org.example.entity.GembaScheduleItem;
import org.example.service.GembaScheduleItemService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/gemba-schedule")
@CrossOrigin
public class GembaScheduleItemController {

    private final GembaScheduleItemService service;

    public GembaScheduleItemController(GembaScheduleItemService service) {
        this.service = service;
    }

    @GetMapping("/date/{date}")
    public List<GembaScheduleItem> getByDate(@PathVariable String date) {
        return service.getByScheduleDate(LocalDate.parse(date));
    }

    @GetMapping("/latest")
    public List<GembaScheduleItem> getLatest() {
        return service.getLatestSchedule();
    }

    @GetMapping("/dates")
    public List<LocalDate> getAvailableDates() {
        return service.getAvailableScheduleDates();
    }

    @PostMapping("/replace/date/{date}")
    public List<GembaScheduleItem> replaceByDate(
            @PathVariable String date,
            @RequestBody List<GembaScheduleItem> items) {
        return service.replaceByDate(LocalDate.parse(date), items);
    }
}
