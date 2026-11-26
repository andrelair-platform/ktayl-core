package com.ktayl.core.finance.domain.reserving;

/** A malformed reserving request (bad method/basis, a reject with no reason) → 422. */
public class InvalidReserveRequestException extends RuntimeException {
    public InvalidReserveRequestException(String message) {
        super(message);
    }
}
