package com.ktayl.core.screening.api;

/**
 * The screening module's published inbound port (ADR-005/013) — the single entry point to screen a party at
 * a business gate, driven by the REST adapter and callable in-process by other modules (policy / billing in
 * S2). Expressed in java/primitive types only; {@code gate} is a free-text marker of the calling gate
 * (e.g. {@code "intake"} / {@code "bind"} / {@code "payment"}); {@code actor} is the authenticated principal,
 * threaded through for audit attribution.
 *
 * <p><b>Fail-closed:</b> if the engine is unavailable the outcome is {@code HOLD} (never silent-ALLOW).
 */
public interface ScreeningGate {

    ScreeningOutcome screen(PartyRef party, String gate, String actor);
}
