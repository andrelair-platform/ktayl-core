package com.ktayl.core.finance.domain;

/**
 * A malformed close/reopen request (a forced close or a reopen without a reason). Mapped to HTTP 422 — the
 * request is well-formed JSON but fails a business rule.
 */
public class InvalidCloseRequestException extends RuntimeException {

    public InvalidCloseRequestException(String message) {
        super(message);
    }
}
