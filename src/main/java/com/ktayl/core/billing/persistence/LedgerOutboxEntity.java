package com.ktayl.core.billing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A pending/posted GL posting (BILL-014, transactional outbox). Written in the SAME transaction as the
 * billing state change it records, then drained to ERPNext by {@code LedgerOutboxDrainer}.
 */
@Entity
@Table(name = "ledger_outbox")
public class LedgerOutboxEntity {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String reference;

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

    protected LedgerOutboxEntity() {} // JPA

    public LedgerOutboxEntity(UUID id, String eventType, String reference, long amountMinor, String currency,
            Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.reference = reference;
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
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getPostedRef() { return postedRef; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPostedAt() { return postedAt; }
}
