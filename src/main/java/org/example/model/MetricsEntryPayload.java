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
    private Map<String, Double> people = new LinkedHashMap<>();
    private Map<String, Double> quality = new LinkedHashMap<>();
    private Map<String, Double> service = new LinkedHashMap<>();
    private Map<String, Double> cost = new LinkedHashMap<>();
}
