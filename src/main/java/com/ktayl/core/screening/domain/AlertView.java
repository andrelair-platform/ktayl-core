package com.ktayl.core.screening.domain;

import java.util.UUID;

/**
 * A read model of an alert joined to its screening + party (domain terms) — what the triage list and the
 * decision use case see. {@code caseStatus} is null until a human has decided it.
 */
public record AlertView(
        UUID alertId,
        UUID screeningId,
        UUID partyId,
        Bucket bucket,
        int score,
        String matchedEntry,
        CaseStatus caseStatus) {
}
