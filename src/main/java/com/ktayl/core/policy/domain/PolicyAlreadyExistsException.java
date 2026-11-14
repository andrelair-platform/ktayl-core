package com.ktayl.core.policy.domain;

/**
 * A policy with that policy_number already exists — maps to 409 (mirrors the PAS create contract). The
 * caller resolves the existing policy via GET ?policy_number=… (the deterministic-policy_number idempotency).
 */
public class PolicyAlreadyExistsException extends RuntimeException {
    public PolicyAlreadyExistsException(String message) {
        super(message);
    }
}
