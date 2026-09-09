package org.example.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "assignment_history")
public class AssignmentHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 80, nullable = false) private String module;
    private Long recordId;
    @Column(length = 160) private String fromAssignee;
    @Column(length = 160) private String toAssignee;
    @Column(length = 160) private String assignedBy;
    @Column(length = 1000) private String remarks;
    private Integer escalationLevel;
    private LocalDateTime assignedAt;

    public Long getId() { return id; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getFromAssignee() { return fromAssignee; }
    public void setFromAssignee(String fromAssignee) { this.fromAssignee = fromAssignee; }
    public String getToAssignee() { return toAssignee; }
    public void setToAssignee(String toAssignee) { this.toAssignee = toAssignee; }
    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public Integer getEscalationLevel() { return escalationLevel; }
    public void setEscalationLevel(Integer escalationLevel) { this.escalationLevel = escalationLevel; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
}
