package com.ktayl.core.submission.domain;

import java.util.UUID;

/**
 * Raised when the handoff of a routed submission to the underwriting (#12) service fails (SUB-02) — any
 * transport / HTTP / response error reaching or being accepted by underwriting. Pure domain — carries no
 * HTTP/framework concept; the web adapter maps it to <b>502 Bad Gateway</b> (the submission IS routed locally,
 * but the downstream handoff failed, so the failure is SURFACED, not silently swallowed).
 *
 * <p>The local {@code route()} transition + save happen BEFORE the handoff, so on failure the submission stays
 * {@link SubmissionStatus#ROUTED} with a null {@code uw_submission_ref} — it is retryable, not left in a
 * half-state.
 */
public class UnderwritingHandoffException extends RuntimeException {

    private final UUID submissionId;

    public UnderwritingHandoffException(UUID submissionId, String message, Throwable cause) {
        super("underwriting handoff failed for submission " + submissionId + ": " + message, cause);
        this.submissionId = submissionId;
    }

    public UUID submissionId() { return submissionId; }
}
