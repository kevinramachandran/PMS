package org.example.controller;

import org.example.entity.PlantMasterDataItem;
import org.example.service.PlantMasterDataService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard-config/master-data")
@CrossOrigin
public class PlantMasterDataController {

    private final PlantMasterDataService service;

    public PlantMasterDataController(PlantMasterDataService service) {
        this.service = service;
    }

    @GetMapping("/{category}")
    public Map<String, Object> list(@PathVariable String category) {
        return success(service.list(category));
    }

    @PostMapping("/{category}")
    public Map<String, Object> add(@PathVariable String category, @RequestBody Map<String, Object> request) {
        try {
            return success(service.add(
                    category,
                    asString(request.get("name")),
                    asString(request.get("parentPlant")),
                    asString(request.get("parentDepartment")),
                    asString(request.get("parentProcessArea"))));
        } catch (IllegalArgumentException ex) {
            return error(ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            return error("Name already exists. Use a different name.");
        }
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            return service.update(
                            id,
                            asString(request.get("name")),
                            request.containsKey("parentPlant") ? asString(request.get("parentPlant")) : null,
                            request.containsKey("parentDepartment") ? asString(request.get("parentDepartment")) : null,
                            request.containsKey("parentProcessArea") ? asString(request.get("parentProcessArea")) : null)
                    .<Map<String, Object>>map(this::success)
                    .orElseGet(() -> error("Item not found"));
        } catch (IllegalArgumentException ex) {
            return error(ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            return error("Name already exists. Use a different name.");
        }
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        try {
            if (!service.delete(id)) {
                return error("Item not found");
            }
            return Map.of("status", "success");
        } catch (IllegalArgumentException ex) {
            return error(ex.getMessage());
        }
    }

    private Map<String, Object> success(List<PlantMasterDataItem> items) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "success");
        payload.put("items", items);
        return payload;
    }

    private Map<String, Object> success(PlantMasterDataItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "success");
        payload.put("item", item);
        return payload;
    }

    private Map<String, Object> error(String message) {
        return Map.of("status", "error", "message", message == null ? "Request failed" : message);
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
