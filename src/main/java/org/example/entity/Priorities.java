package org.example.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "priorities")
public class Priorities {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String priority1;
    private String priority2;
    private String priority3;

    private String type; // TOP_3 or WEEKLY

    private LocalDate date;

    @PrePersist
    public void prePersist() {
        this.date = LocalDate.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPriority1() {
        return priority1;
    }

    public void setPriority1(String priority1) {
        this.priority1 = priority1;
    }

    public String getPriority2() {
        return priority2;
    }

    public void setPriority2(String priority2) {
        this.priority2 = priority2;
    }

    public String getPriority3() {
        return priority3;
    }

    public void setPriority3(String priority3) {
        this.priority3 = priority3;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }
}
