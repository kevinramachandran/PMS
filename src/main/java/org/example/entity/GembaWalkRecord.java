package org.example.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "gemba_walk_records")
public class GembaWalkRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long scheduleItemId;

    @Column(length = 20)
    private String startTime;

    @Column(length = 20)
    private String completionTime;

    @Column(length = 160)
    private String email;

    @Column(length = 160)
    private String managerName;

    private LocalDate dateOfLeadershipSafetyWalkConducted;

    @Column(length = 160)
    private String managementSafetyWalkWeek;

    @Column(length = 160)
    private String locationOfMswConducted;

    @Column(length = 160)
    private String responsibility;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("observationOrder ASC, id ASC")
    private List<GembaWalkObservation> observations = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getScheduleItemId() {
        return scheduleItemId;
    }

    public void setScheduleItemId(Long scheduleItemId) {
        this.scheduleItemId = scheduleItemId;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(String completionTime) {
        this.completionTime = completionTime;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getManagerName() {
        return managerName;
    }

    public void setManagerName(String managerName) {
        this.managerName = managerName;
    }

    public LocalDate getDateOfLeadershipSafetyWalkConducted() {
        return dateOfLeadershipSafetyWalkConducted;
    }

    public void setDateOfLeadershipSafetyWalkConducted(LocalDate dateOfLeadershipSafetyWalkConducted) {
        this.dateOfLeadershipSafetyWalkConducted = dateOfLeadershipSafetyWalkConducted;
    }

    public String getManagementSafetyWalkWeek() {
        return managementSafetyWalkWeek;
    }

    public void setManagementSafetyWalkWeek(String managementSafetyWalkWeek) {
        this.managementSafetyWalkWeek = managementSafetyWalkWeek;
    }

    public String getLocationOfMswConducted() {
        return locationOfMswConducted;
    }

    public void setLocationOfMswConducted(String locationOfMswConducted) {
        this.locationOfMswConducted = locationOfMswConducted;
    }

    public String getResponsibility() {
        return responsibility;
    }

    public void setResponsibility(String responsibility) {
        this.responsibility = responsibility;
    }

    public List<GembaWalkObservation> getObservations() {
        return observations;
    }

    public void setObservations(List<GembaWalkObservation> observations) {
        this.observations = observations;
    }
}
