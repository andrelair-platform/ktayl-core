package com.ktayl.core.claimsgl.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A pending/posted claims GL posting (ADR-021, transactional outbox). Written in the SAME transaction as the
 * event is consumed, then drained to ERPNext by {@code ClaimLedgerOutboxDrainer}. Mirrors billing's
 * {@code LedgerOutboxEntity} (duplication sanctioned WET-first — extract on the 3rd GL consumer, ADR-021).
 *
 * <p>{@code amountMinor} is <b>signed</b> for a reserve movement (+increase / −downward adjustment) and the
 * JE factory flips debit/credit accordingly; for a payment it is {@code > 0}.
 */
@Entity
@Table(name = "claim_ledger_outbox", schema = "claimsgl")
public class ClaimLedgerOutboxEntity {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    /** the event id (dedup + JE remark/idempotency ref) */
    @Column(nullable = false, unique = true)
    private String reference;

    @Column(name = "claim_number", nullable = false)
    private String claimNumber;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

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

    protected ClaimLedgerOutboxEntity() {} // JPA

    public ClaimLedgerOutboxEntity(UUID id, String eventType, String reference, String claimNumber,
            long amountMinor, String currency, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.reference = reference;
        this.claimNumber = claimNumber;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = "pending";
        this.attempts = 0;
        this.createdAt = createdAt;
    }

    /** Mark posted (the drainer, on a successful ERPNext post). */
    public void markPosted(String postedRef, Instant at) {
        this.status = "posted";
        this.postedRef = postedRef;
        this.postedAt = at;
        this.lastError = null;
    }

    /** Record a failed attempt — stays pending for the next drain tick. */
    public void recordFailure(String error) {
        this.attempts += 1;
        this.lastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getReference() { return reference; }
    public String getClaimNumber() { return claimNumber; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getPostedRef() { return postedRef; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPostedAt() { return postedAt; }
}
