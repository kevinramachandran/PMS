package org.example.service;

import org.example.entity.GembaWalkMasterDataItem;
import org.example.repository.GembaWalkMasterDataItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GembaWalkMasterDataService {

    public static final String GEMBA_CATEGORY = "GEMBA_CATEGORY";
    public static final String LIFE_SAVER_RULE = "LIFE_SAVER_RULE";

    private final GembaWalkMasterDataItemRepository repository;

    public GembaWalkMasterDataService(GembaWalkMasterDataItemRepository repository) {
        this.repository = repository;
    }

    public List<GembaWalkMasterDataItem> list(String category) {
        return repository.findByCategoryOrderByNameAsc(normalizeCategory(category));
    }

    public List<String> names(String category) {
        return list(category).stream()
                .map(GembaWalkMasterDataItem::getName)
                .toList();
    }

    public GembaWalkMasterDataItem add(String category, String name) {
        String normalizedCategory = normalizeCategory(category);
        String normalizedName = normalizeName(name);
        rejectDuplicate(normalizedCategory, normalizedName, null);

        GembaWalkMasterDataItem item = new GembaWalkMasterDataItem();
        item.setCategory(normalizedCategory);
        item.setName(normalizedName);
        return repository.save(item);
    }

    public Optional<GembaWalkMasterDataItem> update(Long id, String name) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<GembaWalkMasterDataItem> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        GembaWalkMasterDataItem item = existing.get();
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
        if (GEMBA_CATEGORY.equals(normalized) || LIFE_SAVER_RULE.equals(normalized)) {
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
        Optional<GembaWalkMasterDataItem> duplicate = repository.findByCategoryAndNameIgnoreCase(category, name);
        if (duplicate.isPresent() && (allowedId == null || !duplicate.get().getId().equals(allowedId))) {
            throw new IllegalArgumentException("Name already exists");
        }
    }
}
