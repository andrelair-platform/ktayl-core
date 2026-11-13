package com.ktayl.core.complaints.domain;

/**
 * The complaint lifecycle states (ACPR/EIOPA/IDD). Ordered: a complaint moves forward only
 * {@code REGISTERED → ACKNOWLEDGED → ASSIGNED → RESOLVED → CLOSED} (the state machine is enforced on the
 * {@link Complaint} aggregate — CMPL-012). Pure domain value — no framework.
 */
public enum ComplaintStatus {
    REGISTERED,
    ACKNOWLEDGED,
    ASSIGNED,
    RESOLVED,
    CLOSED
}
