package com.ktayl.core.complaints.domain;

/**
 * A lifecycle transition attempted from the wrong state (e.g. resolve before acknowledge, or any operation
 * on a CLOSED complaint). Pure domain — the web adapter (CMPL-013) maps it to HTTP <b>409 Conflict</b>
 * (distinct from {@link InvalidComplaintException} = bad input → 4xx).
 */
public class IllegalComplaintTransitionException extends RuntimeException {
    public IllegalComplaintTransitionException(ComplaintStatus from, ComplaintStatus required) {
        super("illegal transition: complaint is " + from + " but this operation requires " + required);
    }
}
