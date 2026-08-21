package org.example.model;

import java.time.LocalDate;

public class IssueBoardProgressUpdate {

    private String problem;
    private String priority;
    private String ownerName;
    private String issueDate;
    private String rootCause;
    private String actions;
    private String responsible;
    private LocalDate targetDate;
    private String targetDateRemark;
    private LocalDate targetDateExtension1;
    private String targetDateExtension1Remark;
    private LocalDate targetDateExtension2;
    private String targetDateExtension2Remark;
    private String status;
    private LocalDate completedDate;

    public String getProblem() {
        return problem;
    }

    public void setProblem(String problem) {
        this.problem = problem;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getOwnerName() {
        return ownerName;
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
}
