package org.example.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "issue_board_items")
public class IssueBoardItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer rowOrder;

    @Column(length = 500)
    private String problem;

    @Column(length = 120)
    private String priority;

    @Column(length = 255)
    private String ownerName;

    @Column(length = 40)
    private String issueDate;

    @Column(length = 500)
    private String rootCause;

    @Column(length = 1500)
    private String actions;

    @Column(length = 255)
    private String responsible;

    private LocalDate targetDate;

    @Column(name = "target_date_remark", length = 500)
    private String targetDateRemark;

    private LocalDate targetDateExtension1;

    @Column(name = "target_date_extension1_remark", length = 500)
    private String targetDateExtension1Remark;

    private LocalDate targetDateExtension2;

    @Column(name = "target_date_extension2_remark", length = 500)
    private String targetDateExtension2Remark;

    private Integer dueDays;

    @Column(length = 80)
    private String status;

    private LocalDate completedDate;

    @Column(length = 500)
    private String remarks;

    private LocalDate lastReviewDate;

    private LocalDate nextReviewDate;

    private LocalDate boardDate;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (boardDate == null) {
            boardDate = LocalDate.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getRowOrder() {
        return rowOrder;
    }

    public void setRowOrder(Integer rowOrder) {
        this.rowOrder = rowOrder;
    }

    public String getProblem() {
        return problem;
    }

    public void setProblem(String problem) {
        this.problem = problem;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(String issueDate) {
        this.issueDate = issueDate;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getActions() {
        return actions;
    }

    public void setActions(String actions) {
        this.actions = actions;
    }

    public String getResponsible() {
        return responsible;
    }

    public void setResponsible(String responsible) {
        this.responsible = responsible;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public String getTargetDateRemark() {
        return targetDateRemark;
    }

    public void setTargetDateRemark(String targetDateRemark) {
        this.targetDateRemark = targetDateRemark;
    }

    public LocalDate getTargetDateExtension1() {
        return targetDateExtension1;
    }

    public void setTargetDateExtension1(LocalDate targetDateExtension1) {
        this.targetDateExtension1 = targetDateExtension1;
    }

    public String getTargetDateExtension1Remark() {
        return targetDateExtension1Remark;
    }

    public void setTargetDateExtension1Remark(String targetDateExtension1Remark) {
        this.targetDateExtension1Remark = targetDateExtension1Remark;
    }

    public LocalDate getTargetDateExtension2() {
        return targetDateExtension2;
    }

    public void setTargetDateExtension2(LocalDate targetDateExtension2) {
        this.targetDateExtension2 = targetDateExtension2;
    }

    public String getTargetDateExtension2Remark() {
        return targetDateExtension2Remark;
    }

    public void setTargetDateExtension2Remark(String targetDateExtension2Remark) {
        this.targetDateExtension2Remark = targetDateExtension2Remark;
    }

    public Integer getDueDays() {
        return dueDays;
    }

    public void setDueDays(Integer dueDays) {
        this.dueDays = dueDays;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(LocalDate completedDate) {
        this.completedDate = completedDate;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public LocalDate getLastReviewDate() {
        return lastReviewDate;
    }

    public void setLastReviewDate(LocalDate lastReviewDate) {
        this.lastReviewDate = lastReviewDate;
    }

    public LocalDate getNextReviewDate() {
        return nextReviewDate;
    }

    public void setNextReviewDate(LocalDate nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
    }

    public LocalDate getBoardDate() {
        return boardDate;
    }

    public void setBoardDate(LocalDate boardDate) {
        this.boardDate = boardDate;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
