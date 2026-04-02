package org.example.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "leadership_gemba_tracker")
public class LeadershipGembaTrackerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer rowOrder;

    @Column(length = 160)
    private String managerName;

    @Column(length = 120)
    private String department;

    @Column(length = 600)
    private String areaOfCoverage;

    private Integer targetYtd;
    private Integer targetMtd;

    private Integer week1Target;
    private Integer week1Actual;
    private Integer week2Target;
    private Integer week2Actual;
    private Integer week3Target;
    private Integer week3Actual;
    private Integer week4Target;
    private Integer week4Actual;

    private Double compliancePercent;

    private Boolean week1Closed;
    private Boolean week2Closed;
    private Boolean week3Closed;
    private Boolean week4Closed;

    @Column(length = 40)
    private String periodLabel;

    private LocalDate scheduleDate;

    @PrePersist
    public void prePersist() {
        if (scheduleDate == null) {
            scheduleDate = LocalDate.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getRowOrder() { return rowOrder; }
    public void setRowOrder(Integer rowOrder) { this.rowOrder = rowOrder; }

    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getAreaOfCoverage() { return areaOfCoverage; }
    public void setAreaOfCoverage(String areaOfCoverage) { this.areaOfCoverage = areaOfCoverage; }

    public Integer getTargetYtd() { return targetYtd; }
    public void setTargetYtd(Integer targetYtd) { this.targetYtd = targetYtd; }

    public Integer getTargetMtd() { return targetMtd; }
    public void setTargetMtd(Integer targetMtd) { this.targetMtd = targetMtd; }

    public Integer getWeek1Target() { return week1Target; }
    public void setWeek1Target(Integer week1Target) { this.week1Target = week1Target; }

    public Integer getWeek1Actual() { return week1Actual; }
    public void setWeek1Actual(Integer week1Actual) { this.week1Actual = week1Actual; }

    public Integer getWeek2Target() { return week2Target; }
    public void setWeek2Target(Integer week2Target) { this.week2Target = week2Target; }

    public Integer getWeek2Actual() { return week2Actual; }
    public void setWeek2Actual(Integer week2Actual) { this.week2Actual = week2Actual; }

    public Integer getWeek3Target() { return week3Target; }
    public void setWeek3Target(Integer week3Target) { this.week3Target = week3Target; }

    public Integer getWeek3Actual() { return week3Actual; }
    public void setWeek3Actual(Integer week3Actual) { this.week3Actual = week3Actual; }

    public Integer getWeek4Target() { return week4Target; }
    public void setWeek4Target(Integer week4Target) { this.week4Target = week4Target; }

    public Integer getWeek4Actual() { return week4Actual; }
    public void setWeek4Actual(Integer week4Actual) { this.week4Actual = week4Actual; }

    public Double getCompliancePercent() { return compliancePercent; }
    public void setCompliancePercent(Double compliancePercent) { this.compliancePercent = compliancePercent; }

    public Boolean getWeek1Closed() { return week1Closed; }
    public void setWeek1Closed(Boolean week1Closed) { this.week1Closed = week1Closed; }

    public Boolean getWeek2Closed() { return week2Closed; }
    public void setWeek2Closed(Boolean week2Closed) { this.week2Closed = week2Closed; }

    public Boolean getWeek3Closed() { return week3Closed; }
    public void setWeek3Closed(Boolean week3Closed) { this.week3Closed = week3Closed; }

    public Boolean getWeek4Closed() { return week4Closed; }
    public void setWeek4Closed(Boolean week4Closed) { this.week4Closed = week4Closed; }

    public String getPeriodLabel() { return periodLabel; }
    public void setPeriodLabel(String periodLabel) { this.periodLabel = periodLabel; }

    public LocalDate getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(LocalDate scheduleDate) { this.scheduleDate = scheduleDate; }
}
