package com.ktayl.core.policy.domain;

/** A policy-creation invariant was violated (missing field, bad dates) — maps to 422. */
public class InvalidPolicyException extends RuntimeException {
    public InvalidPolicyException(String message) {
        super(message);
    }
}
