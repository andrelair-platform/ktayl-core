package com.ktayl.core.submission.domain;

/**
 * The submission lifecycle states. A submission moves forward {@code RECEIVED → TRIAGED → ROUTED} (the
 * happy path to underwriting) or into the terminal {@code DECLINED} (via triage). The state machine is
 * enforced on the {@link Submission} aggregate — an illegal move throws
 * {@link IllegalSubmissionTransitionException}. Pure domain value — no framework.
 */
public enum SubmissionStatus {
    RECEIVED,
    TRIAGED,
    ROUTED,
    DECLINED
}
