package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
@Table(name = "production_metric_custom_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductionMetricCustomDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_key", nullable = false, unique = true, length = 128)
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

    @Column(name = "active_flag", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (active == null) {
            active = Boolean.TRUE;
        }
        if (decimals == null) {
            decimals = 2;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}