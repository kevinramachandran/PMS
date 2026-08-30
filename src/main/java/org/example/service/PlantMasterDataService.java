package org.example.service;

import org.example.entity.PlantMasterDataItem;
import org.example.repository.PlantMasterDataItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PlantMasterDataService {

    public static final String DEPARTMENT = "DEPARTMENT";
    public static final String PROCESS_AREA = "PROCESS_AREA";

    private final PlantMasterDataItemRepository repository;

    public PlantMasterDataService(PlantMasterDataItemRepository repository) {
        this.repository = repository;
    }

    public List<PlantMasterDataItem> list(String category) {
        return repository.findByCategoryOrderByNameAsc(normalizeCategory(category));
    }

    public List<String> names(String category) {
        return list(category).stream()
                .map(PlantMasterDataItem::getName)
                .toList();
    }

    public PlantMasterDataItem add(String category, String name) {
        String normalizedCategory = normalizeCategory(category);
        String normalizedName = normalizeName(name);
        rejectDuplicate(normalizedCategory, normalizedName, null);

        PlantMasterDataItem item = new PlantMasterDataItem();
        item.setCategory(normalizedCategory);
        item.setName(normalizedName);
        return repository.save(item);
    }

    public Optional<PlantMasterDataItem> update(Long id, String name) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<PlantMasterDataItem> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        PlantMasterDataItem item = existing.get();
        String normalizedName = normalizeName(name);
        rejectDuplicate(item.getCategory(), normalizedName, id);
        item.setName(normalizedName);
        return Optional.of(repository.save(item));
    }

    public boolean delete(Long id) {
        if (id == null || !repository.existsById(id)) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }

    private String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim().toUpperCase().replace('-', '_');
        if (DEPARTMENT.equals(normalized) || PROCESS_AREA.equals(normalized)) {
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

    private void rejectDuplicate(String category, String name, Long allowedId) {
        Optional<PlantMasterDataItem> duplicate = repository.findByCategoryAndNameIgnoreCase(category, name);
        if (duplicate.isPresent() && (allowedId == null || !duplicate.get().getId().equals(allowedId))) {
            throw new IllegalArgumentException("Name already exists");
        }
    }
}
