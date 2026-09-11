package org.example.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "carlex_process_confirmation_observations")
public class CarlexProcessConfirmationObservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmation_id", nullable = false)
    @JsonIgnore
    private CarlexProcessConfirmation confirmation;

    @Column(name = "group_type", length = 20)
    private String groupType;

    @Column(name = "observation_order")
    private Integer observationOrder;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "counter_measure_actions", columnDefinition = "TEXT")
    private String counterMeasureActions;

    @Column(length = 40)
    private String status;

    @Column(name = "observation_image", length = 255)
    private String observationImage;

    public Long getId() {
        return id;
    }

    public CarlexProcessConfirmation getConfirmation() {
        return confirmation;
    }

    public void setConfirmation(CarlexProcessConfirmation confirmation) {
        this.confirmation = confirmation;
    }

    public String getGroupType() {
        return groupType;
    }

    public void setGroupType(String groupType) {
        this.groupType = groupType;
    }

    public Integer getObservationOrder() {
        return observationOrder;
    }

    public void setObservationOrder(Integer observationOrder) {
        this.observationOrder = observationOrder;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCounterMeasureActions() {
        return counterMeasureActions;
    }

    public void setCounterMeasureActions(String counterMeasureActions) {
        this.counterMeasureActions = counterMeasureActions;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getObservationImage() {
        return observationImage;
    }

    public void setObservationImage(String observationImage) {
        this.observationImage = observationImage;
    }
}
