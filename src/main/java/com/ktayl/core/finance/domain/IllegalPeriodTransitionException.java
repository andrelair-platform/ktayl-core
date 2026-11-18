package com.ktayl.core.finance.domain;

/**
 * An illegal period lifecycle transition (closing an already-closed period, or reopening an open one). Mapped
 * to HTTP 409.
 */
public class IllegalPeriodTransitionException extends RuntimeException {

    public IllegalPeriodTransitionException(String message) {
        super(message);
    }
}
