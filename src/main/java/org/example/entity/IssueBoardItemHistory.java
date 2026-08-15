package org.example.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "issue_board_item_history")
public class IssueBoardItemHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long issueBoardItemId;

    @Column(length = 120)
    private String fieldName;

    @Column(length = 255)
    private String oldValue;

    @Column(length = 255)
    private String newValue;

    @Column(length = 255)
    private String editedBy;

    private LocalDateTime editedAt;

    @PrePersist
    public void prePersist() {
        if (editedAt == null) {
            editedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getIssueBoardItemId() {
        return issueBoardItemId;
    }

    public void setIssueBoardItemId(Long issueBoardItemId) {
        this.issueBoardItemId = issueBoardItemId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getEditedBy() {
        return editedBy;
    }

    public void setEditedBy(String editedBy) {
        this.editedBy = editedBy;
    }

    public LocalDateTime getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(LocalDateTime editedAt) {
        this.editedAt = editedAt;
    }
}
