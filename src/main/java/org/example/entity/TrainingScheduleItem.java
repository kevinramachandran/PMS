package org.example.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "training_schedule_items")
public class TrainingScheduleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer rowOrder;

    @Column(length = 120)
    private String kpiTitle;

    private Integer targetPercent;

    @Column(length = 120)
    private String responsible;

    @Column(length = 40)
    private String periodLabel;

    @Column(length = 255)
    private String trainingName;

    @Column(length = 255)
    private String targetAudience;

    @Column(length = 180)
    private String trainer;

    private LocalDate trainingDate;

    @Column(length = 120)
    private String timeSlot;

    private Double durationHours;

    @Column(length = 180)
    private String venue;

    @Column(length = 180)
    private String fpr;

    @Column(length = 120)
    private String status;

    private LocalDate configDate;

    @PrePersist
    public void prePersist() {
        if (configDate == null) {
            configDate = LocalDate.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getRowOrder() { return rowOrder; }
    public void setRowOrder(Integer rowOrder) { this.rowOrder = rowOrder; }

    public String getKpiTitle() { return kpiTitle; }
    public void setKpiTitle(String kpiTitle) { this.kpiTitle = kpiTitle; }

    public Integer getTargetPercent() { return targetPercent; }
    public void setTargetPercent(Integer targetPercent) { this.targetPercent = targetPercent; }

    public String getResponsible() { return responsible; }
    public void setResponsible(String responsible) { this.responsible = responsible; }

    public String getPeriodLabel() { return periodLabel; }
    public void setPeriodLabel(String periodLabel) { this.periodLabel = periodLabel; }

    public String getTrainingName() { return trainingName; }
    public void setTrainingName(String trainingName) { this.trainingName = trainingName; }

    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }

    public String getTrainer() { return trainer; }
    public void setTrainer(String trainer) { this.trainer = trainer; }

    public LocalDate getTrainingDate() { return trainingDate; }
    public void setTrainingDate(LocalDate trainingDate) { this.trainingDate = trainingDate; }

    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }

    public Double getDurationHours() { return durationHours; }
    public void setDurationHours(Double durationHours) { this.durationHours = durationHours; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public String getFpr() { return fpr; }
    public void setFpr(String fpr) { this.fpr = fpr; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getConfigDate() { return configDate; }
    public void setConfigDate(LocalDate configDate) { this.configDate = configDate; }
}
