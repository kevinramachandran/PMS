package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "abnormality_reporting_records")
public class AbnormalityReportingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type_of_tag", length = 160)
    private String typeOfTag;

    @Column(length = 80)
    private String priority;

    @Column(name = "abnormality_tag_number", length = 160)
    private String abnormalityTagNumber;

    @Column(name = "tag_raised_by", length = 160)
    private String tagRaisedBy;

    @Column(name = "date_raised")
    private LocalDate dateRaised;

    @Column(length = 80)
    private String shift;

    @Column(name = "abnormality_related_to", length = 160)
    private String abnormalityRelatedTo;

    @Column(length = 160)
    private String department;

    @Column(name = "area_machine", length = 160)
    private String areaMachine;

    @Column(length = 160)
    private String component;

    @Column(length = 1000)
    private String description;

    @Column(name = "proposed_action", length = 1000)
    private String proposedAction;

    @Column(name = "picture_image", length = 255)
    private String pictureImage;

    @Column(name = "abnormality_defect_type", length = 160)
    private String abnormalityDefectType;

    @Column(name = "assign_to", length = 160)
    private String assignTo;

    @Column(name = "date_closed")
    private LocalDate dateClosed;

    @Column(name = "tag_status", length = 80)
    private String tagStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTypeOfTag() { return typeOfTag; }
    public void setTypeOfTag(String typeOfTag) { this.typeOfTag = typeOfTag; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getAbnormalityTagNumber() { return abnormalityTagNumber; }
    public void setAbnormalityTagNumber(String abnormalityTagNumber) { this.abnormalityTagNumber = abnormalityTagNumber; }
    public String getTagRaisedBy() { return tagRaisedBy; }
    public void setTagRaisedBy(String tagRaisedBy) { this.tagRaisedBy = tagRaisedBy; }
    public LocalDate getDateRaised() { return dateRaised; }
    public void setDateRaised(LocalDate dateRaised) { this.dateRaised = dateRaised; }
    public String getShift() { return shift; }
    public void setShift(String shift) { this.shift = shift; }
    public String getAbnormalityRelatedTo() { return abnormalityRelatedTo; }
    public void setAbnormalityRelatedTo(String abnormalityRelatedTo) { this.abnormalityRelatedTo = abnormalityRelatedTo; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getAreaMachine() { return areaMachine; }
    public void setAreaMachine(String areaMachine) { this.areaMachine = areaMachine; }
    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getProposedAction() { return proposedAction; }
    public void setProposedAction(String proposedAction) { this.proposedAction = proposedAction; }
    public String getPictureImage() { return pictureImage; }
    public void setPictureImage(String pictureImage) { this.pictureImage = pictureImage; }
    public String getAbnormalityDefectType() { return abnormalityDefectType; }
    public void setAbnormalityDefectType(String abnormalityDefectType) { this.abnormalityDefectType = abnormalityDefectType; }
    public String getAssignTo() { return assignTo; }
    public void setAssignTo(String assignTo) { this.assignTo = assignTo; }
    public LocalDate getDateClosed() { return dateClosed; }
    public void setDateClosed(LocalDate dateClosed) { this.dateClosed = dateClosed; }
    public String getTagStatus() { return tagStatus; }
    public void setTagStatus(String tagStatus) { this.tagStatus = tagStatus; }
}
