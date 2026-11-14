package com.ktayl.core.policy.domain;

/** A lifecycle transition was attempted from an illegal state (e.g. activate a draft) — maps to 409. */
public class IllegalPolicyTransitionException extends RuntimeException {
    public IllegalPolicyTransitionException(String message) {
        super(message);
    }
}
