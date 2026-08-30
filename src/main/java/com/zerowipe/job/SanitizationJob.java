package com.zerowipe.job;

import com.zerowipe.policy.SanitizationMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A sanitization job and its progress through the state machine. Persisted
 * so a job can be audited and inspected after the fact; {@code state},
 * {@code bytesProcessed}, {@code currentPass}, {@code totalPasses},
 * {@code startedAt}, {@code finishedAt} and {@code errorMessage} are
 * mutated by the orchestrator as the job runs.
 */
@Entity
@Table(name = "sanitization_job")
public class SanitizationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String deviceSnapshotJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SanitizationMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SanitizationMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobState state;

    @Column(name = "bytes_processed", nullable = false)
    private long bytesProcessed;

    @Column(name = "total_bytes", nullable = false)
    private long totalBytes;

    @Column(name = "current_pass", nullable = false)
    private int currentPass;

    @Column(name = "total_passes", nullable = false)
    private int totalPasses;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "operator_name", nullable = false)
    private String operatorName;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    protected SanitizationJob() {
        // JPA
    }

    public SanitizationJob(
            String deviceSnapshotJson,
            SanitizationMethod method,
            SanitizationMode mode,
            JobState state,
            long totalBytes,
            int totalPasses,
            String operatorName) {
        this.deviceSnapshotJson = deviceSnapshotJson;
        this.method = method;
        this.mode = mode;
        this.state = state;
        this.totalBytes = totalBytes;
        this.totalPasses = totalPasses;
        this.operatorName = operatorName;
    }

    public Long getId() {
        return id;
    }

    public String getDeviceSnapshotJson() {
        return deviceSnapshotJson;
    }

    public SanitizationMethod getMethod() {
        return method;
    }

    public SanitizationMode getMode() {
        return mode;
    }

    public JobState getState() {
        return state;
    }

    public void setState(JobState state) {
        this.state = state;
    }

    public long getBytesProcessed() {
        return bytesProcessed;
    }

    public void setBytesProcessed(long bytesProcessed) {
        this.bytesProcessed = bytesProcessed;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public int getCurrentPass() {
        return currentPass;
    }

    public void setCurrentPass(int currentPass) {
        this.currentPass = currentPass;
    }

    public int getTotalPasses() {
        return totalPasses;
    }

    public void setTotalPasses(int totalPasses) {
        this.totalPasses = totalPasses;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
