package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.service.DataSyncService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/data-sync")
public class DataSyncController {
    private final DataSyncService service;
    public DataSyncController(DataSyncService service) { this.service = service; }

    @GetMapping("/{dataset}/export-for-sync")
    public ResponseEntity<DataSyncService.CsvExport> exportForSync(@PathVariable String dataset,
            @RequestParam(defaultValue = "") String category, HttpSession session) throws IOException {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.exportForSync(dataset, category, session));
    }

    @GetMapping("/{dataset}/export")
    public ResponseEntity<byte[]> exportCsv(@PathVariable String dataset,
            @RequestParam(defaultValue = "") String category, HttpSession session) throws IOException {
        byte[] csv = service.exportCsv(dataset, category, session).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dataset + "-data-sync.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(csv);
    }

    @GetMapping("/{dataset}/template")
    public ResponseEntity<byte[]> templateCsv(@PathVariable String dataset,
            @RequestParam(defaultValue = "") String category, HttpSession session) throws IOException {
        byte[] csv = service.templateCsv(dataset, category, session).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dataset + "-import-template.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(csv);
    }

    @PostMapping(value = "/{dataset}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importCsv(@PathVariable String dataset, @RequestParam(defaultValue = "") String category,
            @RequestParam("file") MultipartFile file, HttpSession session) throws IOException {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("Choose a CSV file between 1 byte and 5 MB");
        return service.importCsv(dataset, category, new String(file.getBytes(), StandardCharsets.UTF_8), session);
    }

    @ExceptionHandler({IllegalArgumentException.class, IOException.class, UncheckedIOException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Map<String, String>> invalid(Exception ex) {
        String message = ex instanceof DataIntegrityViolationException
                ? "A row conflicts with existing data or exceeds a field limit. No changes were saved."
                : ex.getMessage();
        return ResponseEntity.badRequest().body(Map.of("status", "error", "message", message == null ? "Invalid CSV. No changes were saved." : message));
    }
}
