package org.example.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "hs_daily_cross", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"year", "month", "day"})
})
public class HsDailyCross {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int year;
    private int month; // 1-12
    private int day;   // 1-31

    // ZERO, WITH_LOST, WITHOUT_LOST
    @Column(length = 30)
    private String accidentStatus;

    // NONE, OCCURRED
    @Column(length = 30)
    private String nearMissStatus;

    // NONE, OCCURRED
    @Column(length = 30)
    private String safetyConcernStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }

    public int getDay() { return day; }
    public void setDay(int day) { this.day = day; }

    public String getAccidentStatus() { return accidentStatus; }
    public void setAccidentStatus(String accidentStatus) { this.accidentStatus = accidentStatus; }

    public String getNearMissStatus() { return nearMissStatus; }
    public void setNearMissStatus(String nearMissStatus) { this.nearMissStatus = nearMissStatus; }

    public String getSafetyConcernStatus() { return safetyConcernStatus; }
    public void setSafetyConcernStatus(String safetyConcernStatus) { this.safetyConcernStatus = safetyConcernStatus; }
}
