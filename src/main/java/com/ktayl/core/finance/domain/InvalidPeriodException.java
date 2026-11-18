package com.ktayl.core.finance.domain;

/**
 * A caller supplied a malformed accounting period (not {@code YYYY-MM}). Mapped to HTTP 400 by the finance
 * web adapter — a bad request never surfaces as a 500.
 */
public class InvalidPeriodException extends RuntimeException {

    public InvalidPeriodException(String period) {
        super("invalid accounting period '" + period + "' — expected YYYY-MM");
    }
}
