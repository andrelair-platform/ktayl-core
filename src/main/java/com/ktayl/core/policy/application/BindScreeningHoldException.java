package com.ktayl.core.policy.application;

import java.util.UUID;

/**
 * Raised when the sanctions/AML screening gate returns {@code HOLD} at bind (SUBMITTED → ACTIVE), so the
 * policy MUST NOT be activated — the bind is blocked pending a human compliance decision (RC-02 S2, an
 * LCB-FT / DORA obligation). The gate fails CLOSED (engine unavailable → HOLD), so HOLD uniformly means
 * "blocked": the policy stays SUBMITTED, nothing is saved, and an auditable alert ({@link #alertId()}) is
 * opened for the compliance officer to decide (HITL — the gate never auto-clears). Surfaced as HTTP 409.
 */
public class BindScreeningHoldException extends RuntimeException {

    private final UUID policyId;
    private final UUID alertId;
    private final String gate;

    public BindScreeningHoldException(UUID policyId, UUID alertId, String gate) {
        super("policy " + policyId + " held at the " + gate + " gate pending sanctions review (alert " + alertId + ")");
        this.policyId = policyId;
        this.alertId = alertId;
        this.gate = gate;
    }

    public UUID policyId() { return policyId; }

    public UUID alertId() { return alertId; }

    public String gate() { return gate; }
}
