package org.example.controller;

import org.example.entity.HsDailyCross;
import org.example.service.HsDailyCrossService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hs-daily")
@CrossOrigin
public class HsDailyCrossController {

    private final HsDailyCrossService service;

    public HsDailyCrossController(HsDailyCrossService service) {
        this.service = service;
    }

    @GetMapping("/{year}/{month}")
    public List<HsDailyCross> getByYearAndMonth(
            @PathVariable int year,
            @PathVariable int month) {
        return service.getByYearAndMonth(year, month);
    }

    @PostMapping
    public HsDailyCross save(@RequestBody HsDailyCross data) {
        return service.save(data);
    }

    @PostMapping("/batch")
    public List<HsDailyCross> saveBatch(@RequestBody List<HsDailyCross> dataList) {
        return service.saveAll(dataList);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "Deleted";
    }
}
