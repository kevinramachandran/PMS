package org.example.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

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

    private Integer dueDays;

    @Column(length = 80)
    private String status;

    private LocalDate completedDate;

    @Column(length = 500)
    private String remarks;

    private LocalDate lastReviewDate;

    private LocalDate nextReviewDate;

    private LocalDate boardDate;

    @PrePersist
    public void prePersist() {
        if (boardDate == null) {
            boardDate = LocalDate.now();
        }
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
}
