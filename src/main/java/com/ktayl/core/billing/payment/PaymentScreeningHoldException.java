package com.ktayl.core.billing.payment;

import java.util.UUID;

/**
 * Raised when the sanctions/AML screening gate returns {@code HOLD} at payment initiation, so NO money may
 * move — the PSP is never called (RC-02 S4, the asset-freeze obligation under LCB-FT / DORA). The gate
 * fails CLOSED (engine unavailable → HOLD), so HOLD uniformly means "blocked": no PaymentIntent is created,
 * nothing is persisted, and an auditable alert ({@link #alertId()}) is opened for a human compliance
 * decision (HITL — the gate never auto-clears). Surfaced as HTTP 409.
 */
public class PaymentScreeningHoldException extends RuntimeException {

    private final UUID installmentId;
    private final UUID alertId;
    private final String gate;

    public PaymentScreeningHoldException(UUID installmentId, UUID alertId, String gate) {
        super("payment for installment " + installmentId + " held at the " + gate
                + " gate pending sanctions review (alert " + alertId + ")");
        this.installmentId = installmentId;
        this.alertId = alertId;
        this.gate = gate;
    }

    public UUID installmentId() { return installmentId; }

    public UUID alertId() { return alertId; }

    public String gate() { return gate; }
}
