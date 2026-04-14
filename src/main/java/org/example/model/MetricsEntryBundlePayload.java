package org.example.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricsEntryBundlePayload {

    private LocalDate actualDate;
    private LocalDate targetDate;
    private MetricsEntryPayload actual = new MetricsEntryPayload();
    private MetricsEntryPayload target = new MetricsEntryPayload();
}
