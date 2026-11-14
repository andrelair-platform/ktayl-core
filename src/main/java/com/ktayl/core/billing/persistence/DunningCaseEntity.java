package com.ktayl.core.billing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The dunning case for an installment whose premium collection has failed (BILL-013c). One per installment;
 * tracks the failed-attempt count. Crosses {@code open → lapse_pending} at the lapse threshold, and
 * {@code → resolved} when a later payment finally succeeds.
 */
@Entity
@Table(name = "dunning_case")
public class DunningCaseEntity {

    public static final String OPEN = "open";
    public static final String LAPSE_PENDING = "lapse_pending";
    public static final String RESOLVED = "resolved";

    @Id
    private UUID id;

    @Column(name = "installment_id", nullable = false)
    private UUID installmentId;

    @Column(name = "policy_number", nullable = false)
    private String policyNumber;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_reason")
    private String lastReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DunningCaseEntity() {} // JPA

    public DunningCaseEntity(UUID id, UUID installmentId, String policyNumber, Instant now) {
        this.id = id;
        this.installmentId = installmentId;
        this.policyNumber = policyNumber;
        this.status = OPEN;
        this.attempts = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Record another failed collection attempt. Returns true if this attempt crossed the lapse threshold
     * (i.e. the case just became {@code lapse_pending}) — the caller then raises PolicyLapseRecommended.
     */
    public boolean recordFailure(String reason, int lapseThreshold, Instant now) {
        this.attempts += 1;
        this.lastReason = reason != null && reason.length() > 300 ? reason.substring(0, 300) : reason;
        this.updatedAt = now;
        if (this.attempts >= lapseThreshold && !LAPSE_PENDING.equals(this.status)) {
            this.status = LAPSE_PENDING;
            return true;
        }
        return false;
    }

    /** A later payment succeeded — the collection struggle is over. */
    public void resolve(Instant now) {
        this.status = RESOLVED;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getInstallmentId() { return installmentId; }
    public String getPolicyNumber() { return policyNumber; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastReason() { return lastReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
