package org.example.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "gemba_schedule_items")
public class GembaScheduleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer rowOrder;

    @Column(length = 24)
    private String rowType;

    @Column(length = 255)
    private String functionName;

    @Column(length = 120)
    private String functionType;

    @Column(length = 255)
    private String associateName;

    @Column(length = 24)
    private String functionColor;

    @Column(length = 700)
    private String week1;

    @Column(length = 700)
    private String week2;

    @Column(length = 700)
    private String week3;

    @Column(length = 700)
    private String week4;

    @Column(length = 120)
    private String scheduleMonthLabel;

    private LocalDate scheduleDate;

    @PrePersist
    public void prePersist() {
        if (scheduleDate == null) {
            scheduleDate = LocalDate.now();
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

    public String getRowType() {
        return rowType;
    }

    public void setRowType(String rowType) {
        this.rowType = rowType;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getFunctionColor() {
        return functionColor;
    }

    public void setFunctionColor(String functionColor) {
        this.functionColor = functionColor;
    }

    public String getFunctionType() {
        return functionType;
    }

    public void setFunctionType(String functionType) {
        this.functionType = functionType;
    }

    public String getAssociateName() {
        return associateName;
    }

    public void setAssociateName(String associateName) {
        this.associateName = associateName;
    }

    public String getWeek1() {
        return week1;
    }

    public void setWeek1(String week1) {
        this.week1 = week1;
    }

    public String getWeek2() {
        return week2;
    }

    public void setWeek2(String week2) {
        this.week2 = week2;
    }

    public String getWeek3() {
        return week3;
    }

    public void setWeek3(String week3) {
        this.week3 = week3;
    }

    public String getWeek4() {
        return week4;
    }

    public void setWeek4(String week4) {
        this.week4 = week4;
    }

    public String getScheduleMonthLabel() {
        return scheduleMonthLabel;
    }

    public void setScheduleMonthLabel(String scheduleMonthLabel) {
        this.scheduleMonthLabel = scheduleMonthLabel;
    }

    public LocalDate getScheduleDate() {
        return scheduleDate;
    }

    public void setScheduleDate(LocalDate scheduleDate) {
        this.scheduleDate = scheduleDate;
    }
}
