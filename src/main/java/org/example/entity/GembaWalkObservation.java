package org.example.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "gemba_walk_observations")
public class GembaWalkObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer observationOrder;

    @Column(length = 1000)
    private String observationDescription;

    @Column(length = 255)
    private String pictureImage;

    @Column(length = 160)
    private String gembaCategory;

    @Column(length = 160)
    private String lifeSaverRule;

    @Column(length = 40)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id")
    @JsonIgnore
    private GembaWalkRecord record;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getObservationOrder() {
        return observationOrder;
    }

    public void setObservationOrder(Integer observationOrder) {
        this.observationOrder = observationOrder;
    }

    public String getObservationDescription() {
        return observationDescription;
    }

    public void setObservationDescription(String observationDescription) {
        this.observationDescription = observationDescription;
    }

    public String getPictureImage() {
        return pictureImage;
    }

    public void setPictureImage(String pictureImage) {
        this.pictureImage = pictureImage;
    }

    public String getGembaCategory() {
        return gembaCategory;
    }

    public void setGembaCategory(String gembaCategory) {
        this.gembaCategory = gembaCategory;
    }

    public String getLifeSaverRule() {
        return lifeSaverRule;
    }

    public void setLifeSaverRule(String lifeSaverRule) {
        this.lifeSaverRule = lifeSaverRule;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public GembaWalkRecord getRecord() {
        return record;
    }

    public void setRecord(GembaWalkRecord record) {
        this.record = record;
    }
}
