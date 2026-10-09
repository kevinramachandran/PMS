package org.example.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class DailyPerformanceFieldsConverter implements AttributeConverter<List<DailyPerformanceField>, String> {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Override public String convertToDatabaseColumn(List<DailyPerformanceField> fields) {
        if (fields == null) return null;
        try { return JSON.writeValueAsString(fields); }
        catch (Exception ex) { throw new IllegalArgumentException("Cannot store daily performance fields", ex); }
    }
    @Override public List<DailyPerformanceField> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return null;
        try { return JSON.readValue(value, new TypeReference<List<DailyPerformanceField>>() {}); }
        catch (Exception ex) { throw new IllegalArgumentException("Cannot read daily performance fields", ex); }
    }
}
