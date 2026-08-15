package org.example.controller;

import org.example.entity.AbnormalityTrackerEntry;
import org.example.service.AbnormalityTrackerService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
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

    @GetMapping("/template/csv")
    public ResponseEntity<byte[]> downloadCsvTemplate() {
        String csv = "department,yellowTags,redTags,closurePercent\n"
                + "H&S and CE,0,0,100\n"
                + "Admin,0,0,100\n"
                + "B&P,0,0,100\n"
                + "Packaging,0,0,100\n"
                + "Quality,0,0,100\n"
                + "U&M,0,0,100\n"
                + "CS,0,0,100\n"
                + "Finance,0,0,100\n";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=abnormality-tracker-template.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<AbnormalityTrackerEntry> importCsv(@RequestParam String periodLabel,
                                                   @RequestParam MultipartFile file) throws Exception {
        return service.importCsv(periodLabel, file);
    }
}
