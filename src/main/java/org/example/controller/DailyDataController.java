package org.example.controller;

import org.example.entity.DailyData;
import org.example.service.DailyDataService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/daily-data")
@CrossOrigin
public class DailyDataController {

    private final DailyDataService service;

    public DailyDataController(DailyDataService service) {
        this.service = service;
    }

    // ✅ SAVE
    @PostMapping
    public DailyData save(@RequestBody DailyData data) {
        return service.save(data);
    }

    // ✅ GET ALL
    @GetMapping
    public List<DailyData> getAll() {
        return service.getAll();
    }

    // ✅ GET BY ID
    @GetMapping("/{id}")
    public DailyData getById(@PathVariable Long id) {
        return service.getById(id);
    }

    // ✅ DELETE
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "Deleted successfully";
    }

    // ✅ GET TODAY
    @GetMapping("/today")
    public List<DailyData> getToday() {
        return service.getToday();
    }

    // ✅ GET BY DATE
    @GetMapping("/date/{date}")
    public List<DailyData> getByDate(@PathVariable String date) {
        return service.getByDate(LocalDate.parse(date));
    }

    // ✅ GET BY TYPE AND DATE
    @GetMapping("/type/{type}")
    public List<DailyData> getByType(@PathVariable String type) {
        return service.getByType(type);
    }

    // ✅ GET BY TYPE AND DATE
    @GetMapping("/type/{type}/date/{date}")
    public List<DailyData> getByTypeAndDate(@PathVariable String type, @PathVariable String date) {
        return service.getByTypeAndDate(type, LocalDate.parse(date));
    }

    @GetMapping("/type/{type}/month")
    public List<DailyData> getLatestByTypeAndMonth(
            @PathVariable String type,
            @RequestParam int month,
            @RequestParam int year) {
        return service.getLatestByTypeAndMonth(type, month, year);
    }

    // ✅ REPLACE BY TYPE AND DATE (overwrite mode)
    @PostMapping("/replace/type/{type}/date/{date}")
    public List<DailyData> replaceByTypeAndDate(
            @PathVariable String type,
            @PathVariable String date,
            @RequestBody List<DailyData> dataList) {
        return service.replaceByTypeAndDate(type, LocalDate.parse(date), dataList);
    }
}
