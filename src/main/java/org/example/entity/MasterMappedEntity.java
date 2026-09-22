package org.example.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.MappedSuperclass;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable master identities with the historical name used when the link was established. */
@MappedSuperclass
public abstract class MasterMappedEntity {
    public record Reference(List<Long> ids, String name) {}

    @Column(name = "master_references", columnDefinition = "TEXT")
    @Convert(converter = MasterReferenceConverter.class)
    private Map<String, Reference> masterReferences = new LinkedHashMap<>();

    @JsonIgnore
    public Map<String, Reference> getMasterReferences() {
        if (masterReferences == null) masterReferences = new LinkedHashMap<>();
        return masterReferences;
    }

    public void setMasterReferences(Map<String, Reference> references) {
        masterReferences = new LinkedHashMap<>(references);
    }
}
