package org.example.controller;

import org.example.entity.Priorities;
import org.example.service.PrioritiesService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/priorities")
@CrossOrigin
public class PrioritiesController {

    private final PrioritiesService service;

    public PrioritiesController(PrioritiesService service) {
        this.service = service;
    }

    // ✅ SAVE
    @PostMapping
    public Priorities save(@RequestBody Priorities data) {
        return service.save(data);
    }

    // ✅ GET ALL
    @GetMapping
    public List<Priorities> getAll() {
        return service.getAll();
    }

    // ✅ GET BY ID
    @GetMapping("/{id}")
    public Priorities getById(@PathVariable Long id) {
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
    public List<Priorities> getToday() {
        return service.getToday();
    }

    // ✅ GET BY DATE
    @GetMapping("/date/{date}")
    public List<Priorities> getByDate(@PathVariable String date) {
        return service.getByDate(LocalDate.parse(date));
    }

    // ✅ GET BY TYPE
    @GetMapping("/type/{type}")
    public List<Priorities> getByType(@PathVariable String type) {
        return service.getByType(type);
    }

    // ✅ GET BY TYPE AND DATE
    @GetMapping("/type/{type}/date/{date}")
    public List<Priorities> getByTypeAndDate(@PathVariable String type, @PathVariable String date) {
        return service.getByTypeAndDate(type, LocalDate.parse(date));
    }

    @GetMapping("/type/{type}/month")
    public List<Priorities> getLatestByTypeAndMonth(
            @PathVariable String type,
            @RequestParam int month,
            @RequestParam int year) {
        return service.getLatestByTypeAndMonth(type, month, year);
    }

    // ✅ REPLACE BY TYPE AND DATE (overwrite mode)
    @PostMapping("/replace/type/{type}/date/{date}")
    public Priorities replaceByTypeAndDate(
            @PathVariable String type,
            @PathVariable String date,
            @RequestBody Priorities data) {
        return service.replaceByTypeAndDate(type, LocalDate.parse(date), data);
    }
}
