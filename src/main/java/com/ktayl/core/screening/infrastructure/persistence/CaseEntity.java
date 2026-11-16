package com.ktayl.core.screening.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence shape for a HITL case decision on an alert — the {@code screening.screening_case} table
 * ({@code screening_case} not {@code case}, a SQL keyword). One row per alert (the alert_id is UNIQUE).
 */
@Entity
@Table(name = "screening_case", schema = "screening")
public class CaseEntity {

    @Id
    private UUID id;

    @Column(name = "alert_id", nullable = false, unique = true)
    private UUID alertId;

    @Column(nullable = false)
    private String status;

    @Column
    private String risk;

    @Column
    private String vigilance;

    @Column(name = "actor_id")
    private String actorId;

    @Column
    private String rationale;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected CaseEntity() {} // JPA

    public CaseEntity(UUID id, UUID alertId, String status, String risk, String vigilance, String actorId,
            String rationale, Instant decidedAt) {
        this.id = id;
        this.alertId = alertId;
        this.status = status;
        this.risk = risk;
        this.vigilance = vigilance;
        this.actorId = actorId;
        this.rationale = rationale;
        this.decidedAt = decidedAt;
    }

    public UUID getId() { return id; }
    public UUID getAlertId() { return alertId; }
    public String getStatus() { return status; }
    public String getRisk() { return risk; }
    public String getVigilance() { return vigilance; }
    public String getActorId() { return actorId; }
    public String getRationale() { return rationale; }
    public Instant getDecidedAt() { return decidedAt; }
}
