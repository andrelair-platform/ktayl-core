package com.ktayl.core.complaints.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Published read model for a complaint — the stable wire/cross-module contract. Deliberately uses Strings
 * for {@code channel}/{@code status} (not the internal domain enums) so the contract is decoupled from the
 * domain; the entity is never exposed (ADR-005). Nullable fields are null until the relevant lifecycle step.
 */
public record ComplaintSummary(
        UUID id,
        String channel,
        String subjectRef,
        String summary,
        String status,
        Instant registeredAt,
        Instant acknowledgementDueAt,
        String assignedTo,
        String resolutionNote) {
}
