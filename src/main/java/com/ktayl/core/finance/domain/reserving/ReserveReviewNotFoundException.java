package com.ktayl.core.finance.domain.reserving;

import java.util.UUID;

/** No reserve review with the given id → 404. */
public class ReserveReviewNotFoundException extends RuntimeException {
    public ReserveReviewNotFoundException(UUID id) {
        super("no reserve review: " + id);
    }
}
