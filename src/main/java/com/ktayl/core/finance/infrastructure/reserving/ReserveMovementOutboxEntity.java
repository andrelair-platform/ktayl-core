package com.ktayl.core.finance.infrastructure.reserving;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A pending/posted IBNR reserve-movement GL posting (FIN-03, ADR-022 transactional outbox). Written in the SAME
 * transaction as a reserve review is approved, then drained to ERPNext by {@link ReserveLedgerOutboxDrainer}.
 * {@code amountMinor} is <b>signed</b>: a strengthening (+) or a release (−); the JE factory flips debit/credit
 * accordingly. Mirrors the claims GL outbox (WET-first duplication, ADR-021/022).
 */
@Entity
@Table(name = "reserve_movement_outbox", schema = "finance")
public class ReserveMovementOutboxEntity {

    @Id
    private UUID id;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(nullable = false)
    private String portfolio;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    /** = the review id (dedup + JE remark/idempotency ref). */
    @Column(nullable = false, unique = true)
    private String reference;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "posted_ref")
    private String postedRef;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    protected ReserveMovementOutboxEntity() {} // JPA

    public ReserveMovementOutboxEntity(UUID id, UUID reviewId, String portfolio, long amountMinor,
            String currency, String reference, Instant createdAt) {
        this.id = id;
        this.reviewId = reviewId;
        this.portfolio = portfolio;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.reference = reference;
        this.status = "pending";
        this.attempts = 0;
        this.createdAt = createdAt;
    }

    public void markPosted(String postedRef, Instant at) {
        this.status = "posted";
        this.postedRef = postedRef;
        this.postedAt = at;
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.attempts += 1;
        this.lastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
    }

    public UUID getId() { return id; }
    public UUID getReviewId() { return reviewId; }
    public String getPortfolio() { return portfolio; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getReference() { return reference; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getPostedRef() { return postedRef; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPostedAt() { return postedAt; }
}
