package com.ktayl.core.screening.infrastructure.persistence;

import java.util.UUID;

/**
 * JPQL constructor projection for an alert joined to its screening (party) + optional case (status). A
 * persistence-local record; the adapter maps it to the domain {@link com.ktayl.core.screening.domain.AlertView}.
 * {@code caseStatus} is null when the alert has not yet been decided (left join miss).
 */
record AlertRow(
        UUID alertId,
        UUID screeningId,
        UUID partyId,
        String bucket,
        int score,
        String matchedEntry,
        String caseStatus) {
}
