package org.example.service;

import org.example.entity.AbnormalityMasterDataItem;
import org.example.repository.AbnormalityMasterDataItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AbnormalityMasterDataService {

    public static final String ABT_TAG_TYPE = "ABT_TAG_TYPE";
    public static final String ABNORMALITY_DEFECT_TYPE = "ABNORMALITY_DEFECT_TYPE";

    private final AbnormalityMasterDataItemRepository repository;

    public AbnormalityMasterDataService(AbnormalityMasterDataItemRepository repository) {
        this.repository = repository;
    }

    public List<AbnormalityMasterDataItem> list(String category) {
        return repository.findByCategoryOrderByNameAsc(normalizeCategory(category));
    }

    public List<String> names(String category) {
        return list(category).stream()
                .map(AbnormalityMasterDataItem::getName)
                .toList();
    }

    public AbnormalityMasterDataItem add(String category, String name) {
        String normalizedCategory = normalizeCategory(category);
        String normalizedName = normalizeName(name);
        rejectDuplicate(normalizedCategory, normalizedName, null);

        AbnormalityMasterDataItem item = new AbnormalityMasterDataItem();
        item.setCategory(normalizedCategory);
        item.setName(normalizedName);
        return repository.save(item);
    }

    public Optional<AbnormalityMasterDataItem> update(Long id, String name) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<AbnormalityMasterDataItem> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        AbnormalityMasterDataItem item = existing.get();
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
        if (ABT_TAG_TYPE.equals(normalized) || ABNORMALITY_DEFECT_TYPE.equals(normalized)) {
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
        Optional<AbnormalityMasterDataItem> duplicate = repository.findByCategoryAndNameIgnoreCase(category, name);
        if (duplicate.isPresent() && (allowedId == null || !duplicate.get().getId().equals(allowedId))) {
            throw new IllegalArgumentException("Name already exists");
        }
    }
}
