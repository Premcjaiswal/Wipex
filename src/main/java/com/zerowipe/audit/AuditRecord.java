package com.zerowipe.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One entry in the SHA-256 hash-chained audit log. Append-only by design -
 * this entity exposes no setters; the chain is tamper-evident, not
 * tamper-proof.
 */
@Entity
@Table(name = "audit_record")
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "previous_hash")
    private String previousHash;

    @Column(name = "record_hash", nullable = false)
    private String recordHash;

    protected AuditRecord() {
        // JPA
    }

    public AuditRecord(
            Instant timestamp,
            String eventType,
            Long jobId,
            String payloadJson,
            String previousHash,
            String recordHash) {
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.jobId = jobId;
        this.payloadJson = payloadJson;
        this.previousHash = previousHash;
        this.recordHash = recordHash;
    }

    public Long getId() {
        return id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getJobId() {
        return jobId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getRecordHash() {
        return recordHash;
    }
}
