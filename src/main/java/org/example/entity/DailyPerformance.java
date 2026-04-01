package org.example.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_performance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_daily_performance_date", columnNames = "date")
})
public class DailyPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_target", nullable = false)
    @JsonProperty("month_target")
    private Double monthTarget;

    @Column(name = "actual_mtd", nullable = false)
    @JsonProperty("actual_mtd")
    private Double actualMtd;

    @Column(name = "daily_target", nullable = false)
    @JsonProperty("daily_target")
    private Double dailyTarget;

    @Column(name = "yesterday", nullable = false)
    private Double yesterday;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        if (this.date == null) {
            this.date = LocalDate.now();
        }
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getMonthTarget() {
        return monthTarget;
    }

    public void setMonthTarget(Double monthTarget) {
        this.monthTarget = monthTarget;
    }

    public Double getActualMtd() {
        return actualMtd;
    }

    public void setActualMtd(Double actualMtd) {
        this.actualMtd = actualMtd;
    }

    public Double getDailyTarget() {
        return dailyTarget;
    }

    public void setDailyTarget(Double dailyTarget) {
        this.dailyTarget = dailyTarget;
    }

    public Double getYesterday() {
        return yesterday;
    }

    public void setYesterday(Double yesterday) {
        this.yesterday = yesterday;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
