package com.ktayl.core.screening.domain;

/**
 * OUTBOUND PORT (hexagonal, ADR-013) — the screening engine the domain checks a party against (a sanctions /
 * watchlist matcher). The application depends on THIS interface, not on the concrete provider (yente /
 * OpenSanctions in S1), so the engine is swappable without touching the business logic.
 *
 * <p><b>Fail-closed contract:</b> on an engine outage the implementation MUST throw
 * {@link ScreeningEngineUnavailableException} — it must NEVER return an empty (silent-ALLOW) result. The
 * use case turns that exception into a HOLD (LCB-FT: a party is never cleared because the check was down).
 */
public interface ScreeningEngine {

    /** @throws ScreeningEngineUnavailableException when the engine cannot be reached or errors. */
    ScreeningResult match(Party party);
}
