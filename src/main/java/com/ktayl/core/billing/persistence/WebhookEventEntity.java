package com.ktayl.core.billing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The webhook idempotency ledger (BILL-013b): one row per Stripe event id. Inserting it first makes a
 * replayed event a no-op; {@code processed_at} being null means "parked" — received but not yet applied
 * (e.g. the webhook beat its payment record), so it is reconciled later, never dropped.
 */
@Entity
@Table(name = "webhook_event")
public class WebhookEventEntity {

    @Id
    @Column(name = "psp_event_id")
    private String pspEventId;

    @Column(nullable = false)
    private String type;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected WebhookEventEntity() {} // JPA

    public WebhookEventEntity(String pspEventId, String type, Instant receivedAt) {
        this.pspEventId = pspEventId;
        this.type = type;
        this.receivedAt = receivedAt;
    }

    public void markProcessed(Instant at) {
        this.processedAt = at;
    }

    public String getPspEventId() { return pspEventId; }
    public String getType() { return type; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
}
