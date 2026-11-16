package com.ktayl.core.screening.domain;

import java.util.UUID;

/**
 * OUTBOUND PORT (hexagonal, ADR-013) — the append-only LCB-FT / DORA audit trail for the screening module.
 * One immutable row per action (SCREEN / SCREEN_FAILCLOSED / DECIDE). The application records an entry
 * without knowing it is a JPA row; the adapter lives in {@code infrastructure.persistence}.
 */
public interface ScreeningAuditLog {

    void record(UUID partyId, String action, String actorId, String datasetVersion, String detail);
}
