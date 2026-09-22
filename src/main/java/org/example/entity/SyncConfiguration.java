package org.example.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sync_configurations")
public class SyncConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String cloudUrl = "";

    @Column(nullable = false, length = 160)
    private String cloudUsername = "";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedCloudPassword = "";

    @Column(nullable = false, length = 500)
    private String downloadFolder = "C:/Brewery-PMS/sync/download";

    @Column(nullable = false, length = 500)
    private String processingFolder = "C:/Brewery-PMS/sync/processing";

    @Column(nullable = false, length = 500)
    private String completedFolder = "C:/Brewery-PMS/sync/completed";

    @Column(nullable = false, length = 500)
    private String failedFolder = "C:/Brewery-PMS/sync/failed";

    @Column(nullable = false)
    private int delaySeconds = 60;

    @Column(nullable = false)
    private int intervalMinutes = 15;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String datasets = "plant-master:PLANT\nplant-master:DEPARTMENT\nplant-master:PROCESS_AREA\nplant-master:DESIGNATION\nkaizen-master:CLASSIFICATION_OF_KAIZEN\nabnormality-master:ABT_TAG_TYPE\nabnormality-master:ABNORMALITY_DEFECT_TYPE\nwalk-master:GEMBA_CATEGORY\nwalk-master:LIFE_SAVER_RULE\nprocess-master:ZM_OBSERVATION\nprocess-master:PM_OBSERVATION\nprocess-master:OM_OBSERVATION\nprocess-master:QM_OBSERVATION\nusers\ngemba-walk\ngemba-kaizen\nabnormality\nprocess-confirmation";

    @Column(nullable = false, length = 5)
    private String scheduleTime = "02:00";

    private LocalDateTime lastRunAt;
    private LocalDateTime lastSuccessAt;

    @Column(length = 40)
    private String lastStatus = "IDLE";

    @Column(length = 2000)
    private String lastMessage = "Not run yet";

    public Long getId() { return id; }
    public String getCloudUrl() { return cloudUrl; }
    public void setCloudUrl(String cloudUrl) { this.cloudUrl = cloudUrl; }
    public String getCloudUsername() { return cloudUsername; }
    public void setCloudUsername(String cloudUsername) { this.cloudUsername = cloudUsername; }
    public String getEncryptedCloudPassword() { return encryptedCloudPassword; }
    public void setEncryptedCloudPassword(String encryptedCloudPassword) { this.encryptedCloudPassword = encryptedCloudPassword; }
    public String getDownloadFolder() { return downloadFolder; }
    public void setDownloadFolder(String downloadFolder) { this.downloadFolder = downloadFolder; }
    public String getProcessingFolder() { return processingFolder; }
    public void setProcessingFolder(String processingFolder) { this.processingFolder = processingFolder; }
    public String getCompletedFolder() { return completedFolder; }
    public void setCompletedFolder(String completedFolder) { this.completedFolder = completedFolder; }
    public String getFailedFolder() { return failedFolder; }
    public void setFailedFolder(String failedFolder) { this.failedFolder = failedFolder; }
    public int getDelaySeconds() { return delaySeconds; }
    public void setDelaySeconds(int delaySeconds) { this.delaySeconds = delaySeconds; }
    public int getIntervalMinutes() { return intervalMinutes; }
    public void setIntervalMinutes(int intervalMinutes) { this.intervalMinutes = intervalMinutes; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDatasets() { return datasets; }
    public void setDatasets(String datasets) { this.datasets = datasets; }
    public String getScheduleTime() { return scheduleTime; }
    public void setScheduleTime(String scheduleTime) { this.scheduleTime = scheduleTime; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public LocalDateTime getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(LocalDateTime lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }
    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
}