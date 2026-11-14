package com.ktayl.core.billing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A premium payment against an installment (BILL-013). Idempotent by {@code client_key} (which is also the
 * Stripe idempotency-key). Starts {@code pending}; flips to {@code succeeded} only on the verified webhook.
 */
@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id
    private UUID id;

    @Column(name = "installment_id", nullable = false)
    private UUID installmentId;

    @Column(name = "client_key", nullable = false, unique = true)
    private String clientKey;

    @Column(name = "psp_intent_id")
    private String pspIntentId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    protected PaymentEntity() {} // JPA

    public PaymentEntity(UUID id, UUID installmentId, String clientKey, String pspIntentId,
            long amountMinor, String currency, String status, Instant createdAt) {
        this.id = id;
        this.installmentId = installmentId;
        this.clientKey = clientKey;
        this.pspIntentId = pspIntentId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }

    /** Capture the payment (on the verified webhook): pending → succeeded. */
    public void markSucceeded(Instant at) {
        this.status = "succeeded";
        this.capturedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getInstallmentId() { return installmentId; }
    public String getClientKey() { return clientKey; }
    public String getPspIntentId() { return pspIntentId; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCapturedAt() { return capturedAt; }
}
