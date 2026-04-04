package org.example.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "lsr_daily_tracking", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"year", "month", "day"})
})
public class LsrDailyTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int year;
    private int month; // 1-12
    private int day;   // 1-31

    // LSR 1: TRAFFIC RULES - SAFE or UNSAFE
    @Column(length = 30)
    private String lsr1Status;

    // LSR 2&3: LOTO, SAFEGUARD & EMERGENCY - SAFE or UNSAFE
    @Column(length = 30)
    private String lsr23Status;

    // LSR 4: WORKING AT HEIGHT - SAFE or UNSAFE
    @Column(length = 30)
    private String lsr4Status;

    // LSR 5: CONFINED SPACE ENTRY - SAFE or UNSAFE
    @Column(length = 30)
    private String lsr5Status;

    // CONTRACTOR MANAGEMENT - SAFE or UNSAFE
    @Column(length = 30)
    private String contractorStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }

    public int getDay() { return day; }
    public void setDay(int day) { this.day = day; }

    public String getLsr1Status() { return lsr1Status; }
    public void setLsr1Status(String lsr1Status) { this.lsr1Status = lsr1Status; }

    public String getLsr23Status() { return lsr23Status; }
    public void setLsr23Status(String lsr23Status) { this.lsr23Status = lsr23Status; }

    public String getLsr4Status() { return lsr4Status; }
    public void setLsr4Status(String lsr4Status) { this.lsr4Status = lsr4Status; }

    public String getLsr5Status() { return lsr5Status; }
    public void setLsr5Status(String lsr5Status) { this.lsr5Status = lsr5Status; }

    public String getContractorStatus() { return contractorStatus; }
    public void setContractorStatus(String contractorStatus) { this.contractorStatus = contractorStatus; }
}
