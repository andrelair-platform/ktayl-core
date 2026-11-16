package com.ktayl.core.screening.api;

import java.util.UUID;

/**
 * Published result of a screening (ADR-005) — the stable cross-module / wire contract. {@code verdict} is a
 * String ("ALLOW" / "HOLD") not the internal enum; {@code alertId} is the first alert on a HOLD (null on
 * ALLOW); {@code datasetVersion} is null for a fail-closed HOLD. A caller gates on {@code verdict}: HOLD
 * stops the business action pending the human decision.
 */
public record ScreeningOutcome(String verdict, UUID alertId, String datasetVersion) {
}
