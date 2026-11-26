package com.ktayl.core.finance.domain.reserving;

import java.util.UUID;

/** A decision (approve/reject) was attempted on a review that is already terminal (APPROVED/REJECTED) → 409. */
public class ReserveReviewNotPendingException extends RuntimeException {
    public ReserveReviewNotPendingException(UUID id, ReserveReviewState state) {
        super("reserve review " + id + " is " + state + ", not PENDING — it cannot be decided again");
    }
}
