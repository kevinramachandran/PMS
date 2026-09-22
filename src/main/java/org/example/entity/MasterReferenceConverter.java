package org.example.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.Map;

@Converter
public class MasterReferenceConverter implements AttributeConverter<Map<String, MasterMappedEntity.Reference>, String> {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Override public String convertToDatabaseColumn(Map<String, MasterMappedEntity.Reference> value) {
        try { return JSON.writeValueAsString(value == null ? Map.of() : value); }
        catch (Exception ex) { throw new IllegalArgumentException("Cannot store master references", ex); }
    }
    @Override public Map<String, MasterMappedEntity.Reference> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return new LinkedHashMap<>();
        try { return JSON.readValue(value, new TypeReference<LinkedHashMap<String, MasterMappedEntity.Reference>>() {}); }
        catch (Exception ex) { throw new IllegalArgumentException("Cannot read master references", ex); }
    }
}
