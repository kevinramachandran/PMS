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
    private String ftdActual;
    private String ftdTarget;
    private String mtdActual;
    private String mtdTarget;
    private String ytdActual;
    private String ytdTarget;
}
