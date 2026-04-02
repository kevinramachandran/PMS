package org.example.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "abnormality_tracker")
public class AbnormalityTrackerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer rowOrder;

    @Column(length = 120)
    private String department;

    private Integer yellowTags;

    private Integer redTags;

    private Double closurePercent;

    @Column(length = 60)
    private String periodLabel;

    private LocalDate recordDate;

    @PrePersist
    public void prePersist() {
        if (recordDate == null) {
            recordDate = LocalDate.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getRowOrder() { return rowOrder; }
    public void setRowOrder(Integer rowOrder) { this.rowOrder = rowOrder; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public Integer getYellowTags() { return yellowTags; }
    public void setYellowTags(Integer yellowTags) { this.yellowTags = yellowTags; }

    public Integer getRedTags() { return redTags; }
    public void setRedTags(Integer redTags) { this.redTags = redTags; }

    public Double getClosurePercent() { return closurePercent; }
    public void setClosurePercent(Double closurePercent) { this.closurePercent = closurePercent; }

    public String getPeriodLabel() { return periodLabel; }
    public void setPeriodLabel(String periodLabel) { this.periodLabel = periodLabel; }

    public LocalDate getRecordDate() { return recordDate; }
    public void setRecordDate(LocalDate recordDate) { this.recordDate = recordDate; }
}
