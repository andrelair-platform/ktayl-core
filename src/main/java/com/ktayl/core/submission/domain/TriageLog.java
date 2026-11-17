package com.ktayl.core.submission.domain;

import java.util.UUID;

/**
 * OUTBOUND PORT (hexagonal, ADR-013) — the append-only triage log for the submission module. An interface so
 * the use case records an immutable triage decision (ROUTE / DECLINE / RETURN) without knowing it is a JPA
 * row; the adapter lives in {@code infrastructure.persistence}. Only ever INSERTs.
 */
public interface TriageLog {

    /** Append one immutable triage decision for a submission. {@code actor} is the acting principal. */
    void record(UUID submissionId, Integer completenessScore, String appetiteFlag, TriageDecision decision,
            String reason, String actor);
}
