package org.example.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricsEntryPayload {

    private LocalDate date;
    private String entryType;
    private Map<String, Object> people = new LinkedHashMap<>();
    private Map<String, Object> quality = new LinkedHashMap<>();
    private Map<String, Object> service = new LinkedHashMap<>();
    private Map<String, Object> cost = new LinkedHashMap<>();
}
