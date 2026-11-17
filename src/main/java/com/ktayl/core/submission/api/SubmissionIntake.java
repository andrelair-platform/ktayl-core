package com.ktayl.core.submission.api;

import java.util.UUID;

/**
 * The submission module's published inbound port (ADR-005/013) — the single entry point for capturing and
 * advancing a submission, driven by the REST adapter and callable in-process by other modules (Distribution).
 * Expressed in java/primitive types + api-local command DTOs (no internal domain type leaks across the
 * boundary); {@code actor} is the authenticated principal, threaded through for audit attribution.
 *
 * <p>Failure contract (mapped to HTTP by the web adapter): unknown id → {@code SubmissionNotFoundException}
 * (404); an illegal transition → {@code IllegalSubmissionTransitionException} (409); routing a held
 * submission → {@code SubmissionScreeningHoldException} (409); bad input → {@code InvalidSubmissionException}
 * (4xx). {@link #register} additionally sanctions-screens the holder at intake — a HOLD flags the submission
 * (still captured, cannot be routed until cleared).
 */
public interface SubmissionIntake {

    /** Capture a new submission (screens the holder at intake); returns its id. */
    UUID register(RegisterSubmissionCommand command, String actor);

    /** Append an exposure item to a submission's dossier; returns the exposure id. */
    UUID addExposure(UUID submissionId, AddExposureCommand command, String actor);

    /** Triage a submission (ROUTE → TRIAGED, DECLINE → DECLINED, RETURN → no status change). */
    void triage(UUID submissionId, TriageSubmissionCommand command, String actor);

    /** Route a triaged submission onward (TRIAGED → ROUTED); refused if under a sanctions hold. */
    void route(UUID submissionId, RouteSubmissionCommand command, String actor);

    /** Clear a submission's sanctions hold (the compliance HITL outcome) so it can be routed. */
    void clearScreeningHold(UUID submissionId, String actor);
}
