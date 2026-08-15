package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.model.CustomMetricValueSnapshot;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "production_metrics")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductionMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date", unique = true, nullable = false)
    private LocalDateTime date;

    @Column(name = "entry_type", length = 16)
    private String entryType;

    @Column(name = "target_date")
    private LocalDateTime targetDate;

    // Production Productivity
    @Column(name = "production_productivity_ftd_actual", length = 64)
    private String productionProductivityFtdActual;

    @Column(name = "production_productivity_ftd_target", length = 64)
    private String productionProductivityFtdTarget;

    @Column(name = "production_productivity_mtd_actual", length = 64)
    private String productionProductivityMtdActual;

    @Column(name = "production_productivity_mtd_target", length = 64)
    private String productionProductivityMtdTarget;

    @Column(name = "production_productivity_ytd_actual", length = 64)
    private String productionProductivityYtdActual;

    @Column(name = "production_productivity_ytd_target", length = 64)
    private String productionProductivityYtdTarget;

    // Logistics Productivity
    @Column(name = "logistics_productivity_ftd_actual", length = 64)
    private String logisticsProductivityFtdActual;

    @Column(name = "logistics_productivity_ftd_target", length = 64)
    private String logisticsProductivityFtdTarget;

    @Column(name = "logistics_productivity_mtd_actual", length = 64)
    private String logisticsProductivityMtdActual;

    @Column(name = "logistics_productivity_mtd_target", length = 64)
    private String logisticsProductivityMtdTarget;

    @Column(name = "logistics_productivity_ytd_actual", length = 64)
    private String logisticsProductivityYtdActual;

    @Column(name = "logistics_productivity_ytd_target", length = 64)
    private String logisticsProductivityYtdTarget;

    // KPI Sensory Score
    @Column(name = "kpi_sensory_score_ftd_actual", length = 64)
    private String kpiSensoryScoreFtdActual;

    @Column(name = "kpi_sensory_score_ftd_target", length = 64)
    private String kpiSensoryScoreFtdTarget;

    @Column(name = "kpi_sensory_score_mtd_actual", length = 64)
    private String kpiSensoryScoreMtdActual;

    @Column(name = "kpi_sensory_score_mtd_target", length = 64)
    private String kpiSensoryScoreMtdTarget;

    @Column(name = "kpi_sensory_score_ytd_actual", length = 64)
    private String kpiSensoryScoreYtdActual;

    @Column(name = "kpi_sensory_score_ytd_target", length = 64)
    private String kpiSensoryScoreYtdTarget;

    // KPI Consumer Complaint
    @Column(name = "kpi__consumer_complaint_units_/_mhl_ftd_actual", length = 64)
    private String kpiConsumerComplaintUnitsMhlFtdActual;

    @Column(name = "kpi__consumer_complaint__units_/_mhl_ftd_target", length = 64)
    private String kpiConsumerComplaintUnitsMhlFtdTarget;

    @Column(name = "kpi_consumer_complaint_units_mhl_mtd_actual", length = 64)
    private String kpiConsumerComplaintUnitsMhlMtdActual;

    @Column(name = "kpi_consumer_complaint_units_mhl_mtd_target", length = 64)
    private String kpiConsumerComplaintUnitsMhlMtdTarget;

    @Column(name = "kpi_consumer_complaint_units_mhl_ytd_actual", length = 64)
    private String kpiConsumerComplaintUnitsMhlYtdActual;

    @Column(name = "kpi_consumer_complaint_units_mhl_ytd_target", length = 64)
    private String kpiConsumerComplaintUnitsMhlYtdTarget;

    // KPI Customer Complaint
    @Column(name = "kpi__customer_complaint__units_/_mhl_ftd_actual", length = 64)
    private String kpiCustomerComplaintUnitsMhlFtdActual;

    @Column(name = "kpi__customer_complaint__units_/_mhl_ftd_target", length = 64)
    private String kpiCustomerComplaintUnitsMhlFtdTarget;

    @Column(name = "kpi_customer_complaint_units_mhl_mtd_actual", length = 64)
    private String kpiCustomerComplaintUnitsMhlMtdActual;

    @Column(name = "kpi_customer_complaint_units_mhl_mtd_target", length = 64)
    private String kpiCustomerComplaintUnitsMhlMtdTarget;

    @Column(name = "kpi_customer_complaint_units_mhl_ytd_actual", length = 64)
    private String kpiCustomerComplaintUnitsMhlYtdActual;

    @Column(name = "kpi_customer_complaint_units_mhl_ytd_target", length = 64)
    private String kpiCustomerComplaintUnitsMhlYtdTarget;

    // Process Confirmation B&P
    @Column(name = "process_confirmation_b&p_ftd_actual", length = 64)
    private String processConfirmationBpFtdActual;

    @Column(name = "process_confirmation_b&p_ftd_target", length = 64)
    private String processConfirmationBpFtdTarget;

    @Column(name = "process_confirmation_bp_mtd_actual", length = 64)
    private String processConfirmationBpMtdActual;

    @Column(name = "process_confirmation_bp_mtd_target", length = 64)
    private String processConfirmationBpMtdTarget;

    @Column(name = "process_confirmation_bp_ytd_actual", length = 64)
    private String processConfirmationBpYtdActual;

    @Column(name = "process_confirmation_bp_ytd_target", length = 64)
    private String processConfirmationBpYtdTarget;

    // Process Confirmation Pack
    @Column(name = "process_confirmation_pack_ftd_actual", length = 64)
    private String processConfirmationPackFtdActual;

    @Column(name = "process_confirmation_pack_ftd_target", length = 64)
    private String processConfirmationPackFtdTarget;

    @Column(name = "process_confirmation_pack_mtd_actual", length = 64)
    private String processConfirmationPackMtdActual;

    @Column(name = "process_confirmation_pack_mtd_target", length = 64)
    private String processConfirmationPackMtdTarget;

    @Column(name = "process_confirmation_pack_ytd_actual", length = 64)
    private String processConfirmationPackYtdActual;

    @Column(name = "process_confirmation_pack_ytd_target", length = 64)
    private String processConfirmationPackYtdTarget;

    // KPI OEE
    @Column(name = "kpi__oee__ftd_actual", length = 64)
    private String kpiOeeFtdActual;

    @Column(name = "kpi__oee__ftd_target", length = 64)
    private String kpiOeeFtdTarget;

    @Column(name = "kpi__oee__mtd_actual", length = 64)
    private String kpiOeeMtdActual;

    @Column(name = "kpi__oee__mtd_target", length = 64)
    private String kpiOeeMtdTarget;

    @Column(name = "kpi_oee_ytd_actual", length = 64)
    private String kpiOeeYtdActual;

    @Column(name = "kpi_oee_ytd_target", length = 64)
    private String kpiOeeYtdTarget;

    // KPI Beer Loss
    @Column(name = "kpi__beer_loss__ftd_actual", length = 64)
    private String kpiBeerLossFtdActual;

    @Column(name = "kpi__beer_loss__ftd_target", length = 64)
    private String kpiBeerLossFtdTarget;

    @Column(name = "kpi__beer_loss__mtd_actual", length = 64)
    private String kpiBeerLossMtdActual;

    @Column(name = "kpi__beer_loss__mtd_target", length = 64)
    private String kpiBeerLossMtdTarget;

    @Column(name = "kpi_beer_loss_ytd_actual", length = 64)
    private String kpiBeerLossYtdActual;

    @Column(name = "kpi_beer_loss_ytd_target", length = 64)
    private String kpiBeerLossYtdTarget;

    // KPI WUR
    @Column(name = "kpi__wur_hl/hl_ftd_actual", length = 64)
    private String kpiWurHlHlFtdActual;

    @Column(name = "kpi__wur_hl/hl_ftd_target", length = 64)
    private String kpiWurHlHlFtdTarget;

    @Column(name = "kpi__wur_hl/hl_mtd_actual", length = 64)
    private String kpiWurHlHlMtdActual;

    @Column(name = "kpi__wur_hl/hl_mtd_target", length = 64)
    private String kpiWurHlHlMtdTarget;

    @Column(name = "kpi_wur_hlhl_ytd_actual", length = 64)
    private String kpiWurHlHlYtdActual;

    @Column(name = "kpi_wur_hlhl_ytd_target", length = 64)
    private String kpiWurHlHlYtdTarget;

    // KPI Electricity
    @Column(name = "kpi__electricity_kwh/hl_ftd_actual", length = 64)
    private String kpiElectricityKwhHlFtdActual;

    @Column(name = "kpi__electricity_kwh/hl_ftd_target", length = 64)
    private String kpiElectricityKwhHlFtdTarget;

    @Column(name = "kpi__electricity_kwh/hl_mtd_actual", length = 64)
    private String kpiElectricityKwhHlMtdActual;

    @Column(name = "kpi__electricity_kwh/hl_mtd_target", length = 64)
    private String kpiElectricityKwhHlMtdTarget;

    @Column(name = "kpi_electricity_kwh_hl_ytd_actual", length = 64)
    private String kpiElectricityKwhHlYtdActual;

    @Column(name = "kpi_electricity_kwh_hl_ytd_target", length = 64)
    private String kpiElectricityKwhHlYtdTarget;

    // KPI Energy
    @Column(name = "kpi__energy_kwh/hl_ftd_actual", length = 64)
    private String kpiEnergyKwhHlFtdActual;

    @Column(name = "kpi__energy_kwh/hl_ftd_target", length = 64)
    private String kpiEnergyKwhHlFtdTarget;

    @Column(name = "kpi__energy_kwh/hl_mtd_actual", length = 64)
    private String kpiEnergyKwhHlMtdActual;

    @Column(name = "kpi__energy_kwh/hl_mtd_target", length = 64)
    private String kpiEnergyKwhHlMtdTarget;

    @Column(name = "kpi_energy_kwh_hl_ytd_actual", length = 64)
    private String kpiEnergyKwhHlYtdActual;

    @Column(name = "kpi_energy_kwh_hl_ytd_target", length = 64)
    private String kpiEnergyKwhHlYtdTarget;

    // No. of Brews & Volume
    @Column(name = "no_of_brews_ftd_actual", length = 64)
    private String noOfBrewsFtdActual;

    @Column(name = "no_of_brews_ftd_target", length = 64)
    private String noOfBrewsFtdTarget;

    @Column(name = "no_of_brews_mtd_actual", length = 64)
    private String noOfBrewsMtdActual;

    @Column(name = "no_of_brews_mtd_target", length = 64)
    private String noOfBrewsMtdTarget;

    @Column(name = "no_of_brews_ytd_actual", length = 64)
    private String noOfBrewsYtdActual;

    @Column(name = "no_of_brews_ytd_target", length = 64)
    private String noOfBrewsYtdTarget;

    // Dispatch
    @Column(name = "dispatch_ftd_actual", length = 64)
    private String dispatchFtdActual;

    @Column(name = "dispatch_ftd_target", length = 64)
    private String dispatchFtdTarget;

    @Column(name = "dispatch_mtd_actual", length = 64)
    private String dispatchMtdActual;

    @Column(name = "dispatch_mtd_target", length = 64)
    private String dispatchMtdTarget;

    @Column(name = "dispatch_ytd_actual", length = 64)
    private String dispatchYtdActual;

    @Column(name = "dispatch_ytd_target", length = 64)
    private String dispatchYtdTarget;

    // KPI RGB Ratio
    @Column(name = "kpi__rgb_ratio__ftd_actual", length = 64)
    private String kpiRgbRatioFtdActual;

    @Column(name = "kpi__rgb_ratio__ftd_target", length = 64)
    private String kpiRgbRatioFtdTarget;

    @Column(name = "kpi__rgb_ratio__mtd_actual", length = 64)
    private String kpiRgbRatioMtdActual;

    @Column(name = "kpi__rgb_ratio__mtd_target", length = 64)
    private String kpiRgbRatioMtdTarget;

    @Column(name = "kpi_rgb_ratio_ytd_actual", length = 64)
    private String kpiRgbRatioYtdActual;

    @Column(name = "kpi_rgb_ratio_ytd_target", length = 64)
    private String kpiRgbRatioYtdTarget;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    private List<CustomMetricValueSnapshot> customMetrics = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
