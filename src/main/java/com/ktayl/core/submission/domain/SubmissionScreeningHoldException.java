package com.ktayl.core.submission.domain;

import java.util.UUID;

/**
 * Raised when a routing is attempted on a submission that is under a sanctions/AML screening HOLD (RC-02) —
 * the submission MUST NOT be routed to underwriting until a human compliance officer clears the hold (HITL;
 * the gate never auto-clears). Pure domain — the web adapter maps it to HTTP <b>409 Conflict</b>, carrying
 * the {@link #alertId()} so the caller can link to the screening alert for review. The submission stays in
 * its current state (nothing is routed).
 */
public class SubmissionScreeningHoldException extends RuntimeException {

    private final UUID submissionId;
    private final UUID alertId;

    public SubmissionScreeningHoldException(UUID submissionId, UUID alertId) {
        super("submission " + submissionId + " is under a sanctions hold and cannot be routed (alert " + alertId + ")");
        this.submissionId = submissionId;
        this.alertId = alertId;
    }

    public UUID submissionId() { return submissionId; }

    public UUID alertId() { return alertId; }
}
