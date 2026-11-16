package com.ktayl.core.screening.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A persisted screening event + its alerts, expressed in domain terms (the {@link ScreeningStore} port's
 * aggregate). {@code datasetVersion} is null for a fail-closed HOLD. The {@code alertIds} line up with the
 * {@code hits} by index after a save (the store assigns each alert an id).
 */
public record ScreeningRecord(
        UUID id,
        UUID partyId,
        String gate,
        Instant at,
        String datasetVersion,
        Verdict verdict,
        List<UUID> alertIds,
        List<Hit> hits) {

    public ScreeningRecord {
        alertIds = alertIds == null ? List.of() : List.copyOf(alertIds);
        hits = hits == null ? List.of() : List.copyOf(hits);
    }
}
