package org.example.entity;

import jakarta.persistence.*;

/** Maps record identities between this installation and its configured sync peer. */
@Entity
@Table(name = "sync_record_mappings", uniqueConstraints = @UniqueConstraint(
        name = "uk_sync_peer_record", columnNames = {"peer_instance_id", "dataset_key", "peer_record_id"}))
public class SyncRecordMapping {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "peer_instance_id", nullable = false, length = 36)
    private String peerInstanceId;

    @Column(name = "dataset_key", nullable = false, length = 80)
    private String datasetKey;

    @Column(name = "peer_record_id", nullable = false)
    private Long peerRecordId;

    @Column(name = "local_record_id", nullable = false)
    private Long localRecordId;

    @Column(name = "local_hash", length = 64)
    private String localHash;

    public Long getId() { return id; }
    public String getPeerInstanceId() { return peerInstanceId; }
    public void setPeerInstanceId(String value) { peerInstanceId = value; }
    public String getDatasetKey() { return datasetKey; }
    public void setDatasetKey(String value) { datasetKey = value; }
    public Long getPeerRecordId() { return peerRecordId; }
    public void setPeerRecordId(Long value) { peerRecordId = value; }
    public Long getLocalRecordId() { return localRecordId; }
    public void setLocalRecordId(Long value) { localRecordId = value; }
    public String getLocalHash() { return localHash; }
    public void setLocalHash(String value) { localHash = value; }
}
