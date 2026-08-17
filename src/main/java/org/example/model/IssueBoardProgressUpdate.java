package org.example.model;

import java.time.LocalDate;

public class IssueBoardProgressUpdate {

    private LocalDate targetDate;
    private LocalDate targetDateExtension1;
    private LocalDate targetDateExtension2;
    private String status;
    private LocalDate completedDate;

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public LocalDate getTargetDateExtension1() {
        return targetDateExtension1;
    }

    public void setTargetDateExtension1(LocalDate targetDateExtension1) {
        this.targetDateExtension1 = targetDateExtension1;
    }

    public LocalDate getTargetDateExtension2() {
        return targetDateExtension2;
    }

    public void setTargetDateExtension2(LocalDate targetDateExtension2) {
        this.targetDateExtension2 = targetDateExtension2;
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
