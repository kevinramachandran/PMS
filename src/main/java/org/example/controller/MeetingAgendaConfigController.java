package org.example.controller;

import org.example.entity.MeetingAgendaConfig;
import org.example.service.MeetingAgendaConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/meeting-agenda")
@CrossOrigin
public class MeetingAgendaConfigController {

    private final MeetingAgendaConfigService service;

    public MeetingAgendaConfigController(MeetingAgendaConfigService service) {
        this.service = service;
    }

    @GetMapping("/latest")
    public ResponseEntity<MeetingAgendaConfig> getLatest() {
        return service.getLatest()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/period/{periodLabel}")
    public ResponseEntity<MeetingAgendaConfig> getByPeriod(@PathVariable String periodLabel) {
        return service.getByPeriod(periodLabel)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/periods")
    public List<String> getPeriods() {
        return service.getPeriods();
    }

    @PostMapping("/replace/date/{date}")
    public ResponseEntity<?> replaceByDate(
            @PathVariable String date,
            @RequestBody MeetingAgendaConfig payload) {
        try {
            LocalDate parsedDate = LocalDate.parse(date);
            MeetingAgendaConfig result = service.replaceByDate(parsedDate, payload);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.err.println("Error saving MeetingAgendaConfig: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}
