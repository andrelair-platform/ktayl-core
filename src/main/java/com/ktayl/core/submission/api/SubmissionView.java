package com.ktayl.core.submission.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Published read model for a submission — the stable wire / cross-module contract. Deliberately uses a String
 * for {@code status} (not the internal domain enum) so the contract is decoupled from the domain; the entity
 * is never exposed (ADR-005). Nullable fields are null until the relevant lifecycle step.
 */
public record SubmissionView(
        UUID id,
        String brokerRef,
        String insuredName,
        String productLine,
        String status,
        boolean screeningHold,
        String routedTarget,
        String uwSubmissionRef,
        Instant receivedAt) {
}
