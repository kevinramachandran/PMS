package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "production_metric_system_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductionMetricSystemDefinition {

    @Id
    @Column(name = "metric_key", nullable = false, length = 128)
    private String metricKey;

    @Column(name = "section_name", nullable = false, length = 16)
    private String section;

    @Column(name = "label", nullable = false, length = 160)
    private String label;

    @Column(name = "unit_label", length = 64)
    private String unit;

    @Column(name = "decimal_places")
    private Integer decimals;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = LocalDateTime.now();
        if (decimals == null) {
            decimals = 0;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }
}
