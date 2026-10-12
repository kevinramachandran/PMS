package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Durable idempotency record for an On-Prem report push request. */
@Entity
@Table(name = "sync_push_requests")
public class SyncPushRequest {
    @Id
    @Column(name = "request_key", length = 200)
    private String requestKey;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String value) { payloadHash = value; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String value) { resultJson = value; }
}
