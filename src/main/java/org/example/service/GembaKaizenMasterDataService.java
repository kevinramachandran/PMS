package org.example.service;

import org.example.entity.GembaKaizenMasterDataItem;
import org.example.repository.GembaKaizenMasterDataItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GembaKaizenMasterDataService {

    public static final String CLASSIFICATION_OF_KAIZEN = "CLASSIFICATION_OF_KAIZEN";

    private final GembaKaizenMasterDataItemRepository repository;

    public GembaKaizenMasterDataService(GembaKaizenMasterDataItemRepository repository) {
        this.repository = repository;
    }

    public List<GembaKaizenMasterDataItem> list(String category) {
        return repository.findByCategoryOrderByNameAsc(normalizeCategory(category));
    }

    public GembaKaizenMasterDataItem add(String category, String name) {
        String normalizedCategory = normalizeCategory(category);
        String normalizedName = normalizeName(name);
        rejectDuplicate(normalizedCategory, normalizedName, null);

        GembaKaizenMasterDataItem item = new GembaKaizenMasterDataItem();
        item.setCategory(normalizedCategory);
        item.setName(normalizedName);
        return repository.save(item);
    }

    public Optional<GembaKaizenMasterDataItem> update(Long id, String name) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<GembaKaizenMasterDataItem> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        GembaKaizenMasterDataItem item = existing.get();
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
        if (CLASSIFICATION_OF_KAIZEN.equals(normalized)) {
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
        Optional<GembaKaizenMasterDataItem> duplicate = repository.findByCategoryAndNameIgnoreCase(category, name);
        if (duplicate.isPresent() && (allowedId == null || !duplicate.get().getId().equals(allowedId))) {
            throw new IllegalArgumentException("Name already exists");
        }
    }
}
