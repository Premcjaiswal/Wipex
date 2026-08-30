package com.zerowipe.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The outcome of verifying a sanitization job: strategy used, measured
 * coverage, and pass/fail. Written once after verification completes -
 * never reports a bare "100% sanitized", only the method used and the
 * measured coverage percentage.
 */
@Entity
@Table(name = "verification_result")
public class VerificationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStrategy strategy;

    @Column(name = "sampled_blocks", nullable = false)
    private long sampledBlocks;

    @Column(name = "total_blocks", nullable = false)
    private long totalBlocks;

    @Column(name = "coverage_percent", nullable = false)
    private double coveragePercent;

    @Column(nullable = false)
    private boolean passed;

    @Column(name = "mismatch_count", nullable = false)
    private long mismatchCount;

    @Column(name = "evidence_json", nullable = false, columnDefinition = "TEXT")
    private String evidenceJson;

    protected VerificationResult() {
        // JPA
    }

    public VerificationResult(
            VerificationStrategy strategy,
            long sampledBlocks,
            long totalBlocks,
            double coveragePercent,
            boolean passed,
            long mismatchCount,
            String evidenceJson) {
        this.strategy = strategy;
        this.sampledBlocks = sampledBlocks;
        this.totalBlocks = totalBlocks;
        this.coveragePercent = coveragePercent;
        this.passed = passed;
        this.mismatchCount = mismatchCount;
        this.evidenceJson = evidenceJson;
    }

    public Long getId() {
        return id;
    }

    public VerificationStrategy getStrategy() {
        return strategy;
    }

    public long getSampledBlocks() {
        return sampledBlocks;
    }

    public long getTotalBlocks() {
        return totalBlocks;
    }

    public double getCoveragePercent() {
        return coveragePercent;
    }

    public boolean isPassed() {
        return passed;
    }

    public long getMismatchCount() {
        return mismatchCount;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }
}
