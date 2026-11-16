package com.ktayl.core.screening.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A HITL decision on an alert's case (domain value). {@code risk}/{@code vigilance} may be null for a
 * PENDING hand-off. Persisted by {@link ScreeningStore#saveCase}.
 */
public record CaseDecision(
        UUID alertId,
        CaseStatus status,
        Risk risk,
        Vigilance vigilance,
        String actorId,
        String rationale,
        Instant decidedAt) {
}
