package com.ktayl.core.finance.domain.reserving;

/** Lifecycle of a reserve review (FIN-03, maker-checker). A review is terminal once APPROVED or REJECTED. */
public enum ReserveReviewState {
    /** Submitted by the maker, awaiting an independent checker. */
    PENDING,
    /** Approved by a checker (≠ maker) — the IBNR movement is booked to the GL. */
    APPROVED,
    /** Rejected by a checker (≠ maker) with a reason — nothing is booked. */
    REJECTED
}
