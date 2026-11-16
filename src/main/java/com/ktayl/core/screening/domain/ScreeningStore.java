package com.ktayl.core.screening.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * OUTBOUND PORT (hexagonal, ADR-013) — the domain's view of the screening store. Owns the party, screening,
 * alert and case persistence, expressed in domain terms ({@link Party} / {@link Hit} / {@link Verdict} …);
 * the JPA adapter in {@code infrastructure.persistence} implements it. The application depends only on this.
 */
public interface ScreeningStore {

    /** Persist (upsert) a party and return it with its assigned id. */
    Party saveParty(Party party);

    /** Look up the party with this {@code externalRef}, if any (for idempotency + status lookups). */
    Optional<Party> findPartyByExternalRef(String externalRef);

    Optional<Party> findParty(UUID partyId);

    /**
     * The idempotency anchor: an existing screening for this party + gate on the given calendar day, if one
     * was already recorded (so a repeat the same day returns the prior result rather than re-screening).
     */
    Optional<ScreeningRecord> findScreening(UUID partyId, String gate, LocalDate day);

    /** Persist a screening event + one alert row per hit; returns the record with assigned ids. */
    ScreeningRecord saveScreening(
            UUID partyId, String gate, Instant at, String datasetVersion, Verdict verdict, List<Hit> hits);

    /** The latest verdict for a party (its current screening status), if it has ever been screened. */
    Optional<Verdict> findLatestStatus(UUID partyId);

    /**
     * Keyset page of alerts, optionally filtered by the undecided/decided {@code status} and/or {@code bucket}
     * (both nullable = no filter). Ordered by alert id ascending; {@code afterId} is the exclusive cursor.
     */
    List<AlertView> listAlerts(CaseStatus status, Bucket bucket, UUID afterId, int limit);

    Optional<AlertView> findAlert(UUID alertId);

    /** Persist (insert or update) the HITL case decision for an alert. */
    void saveCase(CaseDecision decision);
}
