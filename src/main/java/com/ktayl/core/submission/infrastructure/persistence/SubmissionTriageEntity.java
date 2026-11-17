package com.ktayl.core.submission.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only triage decision for the submission module. Only ever INSERTed (the audit of ROUTE / DECLINE /
 * RETURN decisions). Explicit {@code schema = "submission"} (schema-per-module, ADR-006).
 */
@Entity
@Table(name = "submission_triage", schema = "submission")
public class SubmissionTriageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "completeness_score")
    private Integer completenessScore;

    @Column(name = "appetite_flag")
    private String appetiteFlag;

    @Column(nullable = false)
    private String decision;

    @Column
    private String reason;

    @Column(nullable = false)
    private String actor;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected SubmissionTriageEntity() {} // JPA

    public SubmissionTriageEntity(UUID submissionId, Integer completenessScore, String appetiteFlag,
            String decision, String reason, String actor, Instant decidedAt) {
        this.submissionId = submissionId;
        this.completenessScore = completenessScore;
        this.appetiteFlag = appetiteFlag;
        this.decision = decision;
        this.reason = reason;
        this.actor = actor;
        this.decidedAt = decidedAt;
    }

    public Long getId() { return id; }
    public UUID getSubmissionId() { return submissionId; }
    public Integer getCompletenessScore() { return completenessScore; }
    public String getAppetiteFlag() { return appetiteFlag; }
    public String getDecision() { return decision; }
    public String getReason() { return reason; }
    public String getActor() { return actor; }
    public Instant getDecidedAt() { return decidedAt; }
}
