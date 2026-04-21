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
    @Column(name = "production_productivity_ftd_actual")
    private Double productionProductivityFtdActual;

    @Column(name = "production_productivity_ftd_target")
    private Double productionProductivityFtdTarget;

    @Column(name = "production_productivity_mtd_actual")
    private Double productionProductivityMtdActual;

    @Column(name = "production_productivity_mtd_target")
    private Double productionProductivityMtdTarget;

    @Column(name = "production_productivity_ytd_actual")
    private Double productionProductivityYtdActual;

    @Column(name = "production_productivity_ytd_target")
    private Double productionProductivityYtdTarget;

    // Logistics Productivity
    @Column(name = "logistics_productivity_ftd_actual")
    private Double logisticsProductivityFtdActual;

    @Column(name = "logistics_productivity_ftd_target")
    private Double logisticsProductivityFtdTarget;

    @Column(name = "logistics_productivity_mtd_actual")
    private Double logisticsProductivityMtdActual;

    @Column(name = "logistics_productivity_mtd_target")
    private Double logisticsProductivityMtdTarget;

    @Column(name = "logistics_productivity_ytd_actual")
    private Double logisticsProductivityYtdActual;

    @Column(name = "logistics_productivity_ytd_target")
    private Double logisticsProductivityYtdTarget;

    // KPI Sensory Score
    @Column(name = "kpi_sensory_score_ftd_actual")
    private Double kpiSensoryScoreFtdActual;

    @Column(name = "kpi_sensory_score_ftd_target")
    private Double kpiSensoryScoreFtdTarget;

    @Column(name = "kpi_sensory_score_mtd_actual")
    private Double kpiSensoryScoreMtdActual;

    @Column(name = "kpi_sensory_score_mtd_target")
    private Double kpiSensoryScoreMtdTarget;

    @Column(name = "kpi_sensory_score_ytd_actual")
    private Double kpiSensoryScoreYtdActual;

    @Column(name = "kpi_sensory_score_ytd_target")
    private Double kpiSensoryScoreYtdTarget;

    // KPI Consumer Complaint
    @Column(name = "kpi__consumer_complaint_units_/_mhl_ftd_actual")
    private Double kpiConsumerComplaintUnitsMhlFtdActual;

    @Column(name = "kpi__consumer_complaint__units_/_mhl_ftd_target")
    private Double kpiConsumerComplaintUnitsMhlFtdTarget;

    @Column(name = "kpi_consumer_complaint_units_mhl_mtd_actual")
    private Double kpiConsumerComplaintUnitsMhlMtdActual;

    @Column(name = "kpi_consumer_complaint_units_mhl_mtd_target")
    private Double kpiConsumerComplaintUnitsMhlMtdTarget;

    @Column(name = "kpi_consumer_complaint_units_mhl_ytd_actual")
    private Double kpiConsumerComplaintUnitsMhlYtdActual;

    @Column(name = "kpi_consumer_complaint_units_mhl_ytd_target")
    private Double kpiConsumerComplaintUnitsMhlYtdTarget;

    // KPI Customer Complaint
    @Column(name = "kpi__customer_complaint__units_/_mhl_ftd_actual")
    private Double kpiCustomerComplaintUnitsMhlFtdActual;

    @Column(name = "kpi__customer_complaint__units_/_mhl_ftd_target")
    private Double kpiCustomerComplaintUnitsMhlFtdTarget;

    @Column(name = "kpi_customer_complaint_units_mhl_mtd_actual")
    private Double kpiCustomerComplaintUnitsMhlMtdActual;

    @Column(name = "kpi_customer_complaint_units_mhl_mtd_target")
    private Double kpiCustomerComplaintUnitsMhlMtdTarget;

    @Column(name = "kpi_customer_complaint_units_mhl_ytd_actual")
    private Double kpiCustomerComplaintUnitsMhlYtdActual;

    @Column(name = "kpi_customer_complaint_units_mhl_ytd_target")
    private Double kpiCustomerComplaintUnitsMhlYtdTarget;

    // Process Confirmation B&P
    @Column(name = "process_confirmation_b&p_ftd_actual")
    private Double processConfirmationBpFtdActual;

    @Column(name = "process_confirmation_b&p_ftd_target")
    private Double processConfirmationBpFtdTarget;

    @Column(name = "process_confirmation_bp_mtd_actual")
    private Double processConfirmationBpMtdActual;

    @Column(name = "process_confirmation_bp_mtd_target")
    private Double processConfirmationBpMtdTarget;

    @Column(name = "process_confirmation_bp_ytd_actual")
    private Double processConfirmationBpYtdActual;

    @Column(name = "process_confirmation_bp_ytd_target")
    private Double processConfirmationBpYtdTarget;

    // Process Confirmation Pack
    @Column(name = "process_confirmation_pack_ftd_actual")
    private Double processConfirmationPackFtdActual;

    @Column(name = "process_confirmation_pack_ftd_target")
    private Double processConfirmationPackFtdTarget;

    @Column(name = "process_confirmation_pack_mtd_actual")
    private Double processConfirmationPackMtdActual;

    @Column(name = "process_confirmation_pack_mtd_target")
    private Double processConfirmationPackMtdTarget;

    @Column(name = "process_confirmation_pack_ytd_actual")
    private Double processConfirmationPackYtdActual;

    @Column(name = "process_confirmation_pack_ytd_target")
    private Double processConfirmationPackYtdTarget;

    // KPI OEE
    @Column(name = "kpi__oee__ftd_actual")
    private Double kpiOeeFtdActual;

    @Column(name = "kpi__oee__ftd_target")
    private Double kpiOeeFtdTarget;

    @Column(name = "kpi__oee__mtd_actual")
    private Double kpiOeeMtdActual;

    @Column(name = "kpi__oee__mtd_target")
    private Double kpiOeeMtdTarget;

    @Column(name = "kpi_oee_ytd_actual")
    private Double kpiOeeYtdActual;

    @Column(name = "kpi_oee_ytd_target")
    private Double kpiOeeYtdTarget;

    // KPI Beer Loss
    @Column(name = "kpi__beer_loss__ftd_actual")
    private Double kpiBeerLossFtdActual;

    @Column(name = "kpi__beer_loss__ftd_target")
    private Double kpiBeerLossFtdTarget;

    @Column(name = "kpi__beer_loss__mtd_actual")
    private Double kpiBeerLossMtdActual;

    @Column(name = "kpi__beer_loss__mtd_target")
    private Double kpiBeerLossMtdTarget;

    @Column(name = "kpi_beer_loss_ytd_actual")
    private Double kpiBeerLossYtdActual;

    @Column(name = "kpi_beer_loss_ytd_target")
    private Double kpiBeerLossYtdTarget;

    // KPI WUR
    @Column(name = "kpi__wur_hl/hl_ftd_actual")
    private Double kpiWurHlHlFtdActual;

    @Column(name = "kpi__wur_hl/hl_ftd_target")
    private Double kpiWurHlHlFtdTarget;

    @Column(name = "kpi__wur_hl/hl_mtd_actual")
    private Double kpiWurHlHlMtdActual;

    @Column(name = "kpi__wur_hl/hl_mtd_target")
    private Double kpiWurHlHlMtdTarget;

    @Column(name = "kpi_wur_hlhl_ytd_actual")
    private Double kpiWurHlHlYtdActual;

    @Column(name = "kpi_wur_hlhl_ytd_target")
    private Double kpiWurHlHlYtdTarget;

    // KPI Electricity
    @Column(name = "kpi__electricity_kwh/hl_ftd_actual")
    private Double kpiElectricityKwhHlFtdActual;

    @Column(name = "kpi__electricity_kwh/hl_ftd_target")
    private Double kpiElectricityKwhHlFtdTarget;

    @Column(name = "kpi__electricity_kwh/hl_mtd_actual")
    private Double kpiElectricityKwhHlMtdActual;

    @Column(name = "kpi__electricity_kwh/hl_mtd_target")
    private Double kpiElectricityKwhHlMtdTarget;

    @Column(name = "kpi_electricity_kwh_hl_ytd_actual")
    private Double kpiElectricityKwhHlYtdActual;

    @Column(name = "kpi_electricity_kwh_hl_ytd_target")
    private Double kpiElectricityKwhHlYtdTarget;

    // KPI Energy
    @Column(name = "kpi__energy_kwh/hl_ftd_actual")
    private Double kpiEnergyKwhHlFtdActual;

    @Column(name = "kpi__energy_kwh/hl_ftd_target")
    private Double kpiEnergyKwhHlFtdTarget;

    @Column(name = "kpi__energy_kwh/hl_mtd_actual")
    private Double kpiEnergyKwhHlMtdActual;

    @Column(name = "kpi__energy_kwh/hl_mtd_target")
    private Double kpiEnergyKwhHlMtdTarget;

    @Column(name = "kpi_energy_kwh_hl_ytd_actual")
    private Double kpiEnergyKwhHlYtdActual;

    @Column(name = "kpi_energy_kwh_hl_ytd_target")
    private Double kpiEnergyKwhHlYtdTarget;

    // No. of Brews & Volume
    @Column(name = "no_of_brews_ftd_actual")
    private Double noOfBrewsFtdActual;

    @Column(name = "no_of_brews_ftd_target")
    private Double noOfBrewsFtdTarget;

    @Column(name = "no_of_brews_mtd_actual")
    private Double noOfBrewsMtdActual;

    @Column(name = "no_of_brews_mtd_target")
    private Double noOfBrewsMtdTarget;

    @Column(name = "no_of_brews_ytd_actual")
    private Double noOfBrewsYtdActual;

    @Column(name = "no_of_brews_ytd_target")
    private Double noOfBrewsYtdTarget;

    // Dispatch
    @Column(name = "dispatch_ftd_actual")
    private Double dispatchFtdActual;

    @Column(name = "dispatch_ftd_target")
    private Double dispatchFtdTarget;

    @Column(name = "dispatch_mtd_actual")
    private Double dispatchMtdActual;

    @Column(name = "dispatch_mtd_target")
    private Double dispatchMtdTarget;

    @Column(name = "dispatch_ytd_actual")
    private Double dispatchYtdActual;

    @Column(name = "dispatch_ytd_target")
    private Double dispatchYtdTarget;

    // KPI RGB Ratio
    @Column(name = "kpi__rgb_ratio__ftd_actual")
    private Double kpiRgbRatioFtdActual;

    @Column(name = "kpi__rgb_ratio__ftd_target")
    private Double kpiRgbRatioFtdTarget;

    @Column(name = "kpi__rgb_ratio__mtd_actual")
    private Double kpiRgbRatioMtdActual;

    @Column(name = "kpi__rgb_ratio__mtd_target")
    private Double kpiRgbRatioMtdTarget;

    @Column(name = "kpi_rgb_ratio_ytd_actual")
    private Double kpiRgbRatioYtdActual;

    @Column(name = "kpi_rgb_ratio_ytd_target")
    private Double kpiRgbRatioYtdTarget;

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
