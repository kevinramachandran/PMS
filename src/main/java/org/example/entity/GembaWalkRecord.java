package org.example.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Column(length = 120)
    private String department;

    @Column(length = 160)
    private String createdBy;

    @Column(length = 120)
    private String creatorDepartment;

    @Column(length = 120)
    private String creatorArea;

    private LocalDate dateOfLeadershipSafetyWalkConducted;

    @Column(length = 160)
    private String managementSafetyWalkWeek;

    @Column(length = 160)
    private String locationOfMswConducted;

    @Column(length = 160)
    private String responsibility;

    @Column(length = 160)
    private String reassignedTo1;

    @Column(length = 1000)
    private String reassignment1Remark;

    @Column(length = 160)
    private String reassignedTo2;

    @Column(length = 1000)
    private String reassignment2Remark;

    @Column(length = 1000)
    private String finalComments;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    private String assignmentRemark;

    @Transient
    private String displayManagerName;

    @Transient
    private String displayEmail;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("observationOrder ASC, id ASC")
    private List<GembaWalkObservation> observations = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

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

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatorDepartment() {
        return creatorDepartment;
    }

    public void setCreatorDepartment(String creatorDepartment) {
        this.creatorDepartment = creatorDepartment;
    }

    public String getCreatorArea() {
        return creatorArea;
    }

    public void setCreatorArea(String creatorArea) {
        this.creatorArea = creatorArea;
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

    public String getReassignedTo1() { return reassignedTo1; }
    public void setReassignedTo1(String reassignedTo1) { this.reassignedTo1 = reassignedTo1; }
    public String getReassignment1Remark() { return reassignment1Remark; }
    public void setReassignment1Remark(String reassignment1Remark) { this.reassignment1Remark = reassignment1Remark; }
    public String getReassignedTo2() { return reassignedTo2; }
    public void setReassignedTo2(String reassignedTo2) { this.reassignedTo2 = reassignedTo2; }
    public String getReassignment2Remark() { return reassignment2Remark; }
    public void setReassignment2Remark(String reassignment2Remark) { this.reassignment2Remark = reassignment2Remark; }

    public String getFinalComments() {
        return finalComments;
    }

    public void setFinalComments(String finalComments) {
        this.finalComments = finalComments;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getAssignmentRemark() { return assignmentRemark; }
    public void setAssignmentRemark(String assignmentRemark) { this.assignmentRemark = assignmentRemark; }

    public String getDisplayManagerName() {
        return displayManagerName;
    }

    public void setDisplayManagerName(String displayManagerName) {
        this.displayManagerName = displayManagerName;
    }

    public String getDisplayEmail() {
        return displayEmail;
    }

    public void setDisplayEmail(String displayEmail) {
        this.displayEmail = displayEmail;
    }

    public List<GembaWalkObservation> getObservations() {
        return observations;
    }

    public void setObservations(List<GembaWalkObservation> observations) {
        this.observations = observations;
    }
}
