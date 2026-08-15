package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "production_metric_custom_values",
        uniqueConstraints = @UniqueConstraint(columnNames = {"production_metrics_id", "definition_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductionMetricCustomValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "production_metrics_id", nullable = false)
    private ProductionMetrics productionMetrics;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private ProductionMetricCustomDefinition definition;

    @Column(name = "ftd_actual", length = 64)
    private String ftdActual;

    @Column(name = "ftd_target", length = 64)
    private String ftdTarget;

    @Column(name = "mtd_actual", length = 64)
    private String mtdActual;

    @Column(name = "mtd_target", length = 64)
    private String mtdTarget;

    @Column(name = "ytd_actual", length = 64)
    private String ytdActual;

    @Column(name = "ytd_target", length = 64)
    private String ytdTarget;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
