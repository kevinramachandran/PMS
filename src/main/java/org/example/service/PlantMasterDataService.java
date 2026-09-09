package org.example.service;

import org.example.entity.PlantMasterDataItem;
import org.example.repository.PlantMasterDataItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PlantMasterDataService {

    public static final String PLANT = "PLANT";
    public static final String DEPARTMENT = "DEPARTMENT";
    public static final String PROCESS_AREA = "PROCESS_AREA";

    private final PlantMasterDataItemRepository repository;

    public PlantMasterDataService(PlantMasterDataItemRepository repository) {
        this.repository = repository;
    }

    public List<PlantMasterDataItem> list(String category) {
        return repository.findByCategoryOrderByParentPlantAscParentDepartmentAscNameAsc(normalizeCategory(category));
    }

    public List<String> names(String category) {
        return list(category).stream()
                .map(PlantMasterDataItem::getName)
                .toList();
    }

    public PlantMasterDataItem add(String category, String name) {
        return add(category, name, "", "");
    }

    public PlantMasterDataItem add(String category, String name, String parentPlant, String parentDepartment) {
        String normalizedCategory = normalizeCategory(category);
        String normalizedName = normalizeName(name);
        String normalizedParentPlant = normalizeParentPlant(normalizedCategory, parentPlant);
        String normalizedParentDepartment = normalizeParentDepartment(normalizedCategory, normalizedParentPlant, parentDepartment);
        rejectDuplicate(normalizedCategory, normalizedName, normalizedParentPlant, normalizedParentDepartment, null);

        PlantMasterDataItem item = new PlantMasterDataItem();
        item.setCategory(normalizedCategory);
        item.setName(normalizedName);
        item.setParentPlant(emptyToNull(normalizedParentPlant));
        item.setParentDepartment(emptyToNull(normalizedParentDepartment));
        return repository.save(item);
    }

    public Optional<PlantMasterDataItem> update(Long id, String name) {
        return update(id, name, null, null);
    }

    public Optional<PlantMasterDataItem> update(Long id, String name, String parentPlant, String parentDepartment) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<PlantMasterDataItem> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        PlantMasterDataItem item = existing.get();
        String normalizedName = normalizeName(name);
        String normalizedParentPlant = parentPlant == null
                ? trim(item.getParentPlant())
                : normalizeParentPlant(item.getCategory(), parentPlant);
        String normalizedParentDepartment = parentDepartment == null
                ? trim(item.getParentDepartment())
                : normalizeParentDepartment(item.getCategory(), normalizedParentPlant, parentDepartment);
        rejectDuplicate(item.getCategory(), normalizedName, normalizedParentPlant, normalizedParentDepartment, id);
        item.setName(normalizedName);
        item.setParentPlant(emptyToNull(normalizedParentPlant));
        item.setParentDepartment(emptyToNull(normalizedParentDepartment));
        return Optional.of(repository.save(item));
    }

    public boolean delete(Long id) {
        if (id == null) {
            return false;
        }
        Optional<PlantMasterDataItem> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return false;
        }
        rejectDeleteWithChildren(existing.get());
        repository.deleteById(id);
        return true;
    }

    private String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim().toUpperCase().replace('-', '_');
        if (PLANT.equals(normalized) || DEPARTMENT.equals(normalized) || PROCESS_AREA.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported category");
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        return name.trim();
    }

    private String normalizeParentPlant(String category, String parentPlant) {
        if (PLANT.equals(category)) {
            return "";
        }
        String normalized = normalizeName(parentPlant);
        if (!exists(PLANT, normalized, "", "")) {
            throw new IllegalArgumentException("Plant must be configured before adding " + label(category));
        }
        return normalized;
    }

    private String normalizeParentDepartment(String category, String parentPlant, String parentDepartment) {
        if (!PROCESS_AREA.equals(category)) {
            return "";
        }
        String normalized = normalizeName(parentDepartment);
        if (!exists(DEPARTMENT, normalized, parentPlant, "")) {
            throw new IllegalArgumentException("Department must be configured under the selected Plant before adding Area");
        }
        return normalized;
    }

    private boolean exists(String category, String name, String parentPlant, String parentDepartment) {
        String expectedPlant = trim(parentPlant);
        String expectedDepartment = trim(parentDepartment);
        return list(category).stream().anyMatch(item ->
                item.getName().equalsIgnoreCase(name)
                        && trim(item.getParentPlant()).equalsIgnoreCase(expectedPlant)
                        && trim(item.getParentDepartment()).equalsIgnoreCase(expectedDepartment));
    }

    private void rejectDuplicate(String category, String name, String parentPlant, String parentDepartment, Long allowedId) {
        String expectedPlant = trim(parentPlant);
        String expectedDepartment = trim(parentDepartment);
        Optional<PlantMasterDataItem> duplicate = list(category).stream()
                .filter(item -> item.getName().equalsIgnoreCase(name))
                .filter(item -> trim(item.getParentPlant()).equalsIgnoreCase(expectedPlant))
                .filter(item -> trim(item.getParentDepartment()).equalsIgnoreCase(expectedDepartment))
                .findFirst();
        if (duplicate.isPresent() && (allowedId == null || !duplicate.get().getId().equals(allowedId))) {
            throw new IllegalArgumentException("Name already exists");
        }
    }

    private void rejectDeleteWithChildren(PlantMasterDataItem item) {
        if (PLANT.equals(item.getCategory())) {
            boolean hasChildren = list(DEPARTMENT).stream()
                    .anyMatch(child -> trim(child.getParentPlant()).equalsIgnoreCase(item.getName()));
            if (hasChildren) {
                throw new IllegalArgumentException("Delete departments under this Plant before deleting the Plant");
            }
        }
        if (DEPARTMENT.equals(item.getCategory())) {
            boolean hasChildren = list(PROCESS_AREA).stream()
                    .anyMatch(child -> trim(child.getParentPlant()).equalsIgnoreCase(trim(item.getParentPlant()))
                            && trim(child.getParentDepartment()).equalsIgnoreCase(item.getName()));
            if (hasChildren) {
                throw new IllegalArgumentException("Delete areas under this Department before deleting the Department");
            }
        }
    }

    private String label(String category) {
        if (DEPARTMENT.equals(category)) {
            return "Department";
        }
        if (PROCESS_AREA.equals(category)) {
            return "Area";
        }
        return "Plant";
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String emptyToNull(String value) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
