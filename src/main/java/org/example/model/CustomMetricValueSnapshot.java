package org.example.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomMetricValueSnapshot {

    private Long definitionId;
    private String metricKey;
    private String section;
    private String label;
    private String unit;
    private Integer decimals;
    private Integer displayOrder;
    private Double ftdActual;
    private Double ftdTarget;
    private Double mtdActual;
    private Double mtdTarget;
    private Double ytdActual;
    private Double ytdTarget;
}