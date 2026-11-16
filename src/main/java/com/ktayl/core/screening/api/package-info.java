/**
 * The screening module's PUBLISHED surface (ADR-005) — the only package other modules may depend on. A
 * Spring Modulith {@link org.springframework.modulith.NamedInterface named interface} so the boundary is
 * enforced: cross-module callers (policy / billing, S2) use {@link com.ktayl.core.screening.api.ScreeningGate}
 * + its api-local DTOs ({@link com.ktayl.core.screening.api.PartyRef} /
 * {@link com.ktayl.core.screening.api.ScreeningOutcome}), never the internal domain/application/infrastructure
 * types. The contract is expressed in java/primitive types (verdict as a String) so the internal domain can
 * evolve without breaking consumers.
 */
@org.springframework.modulith.NamedInterface("api")
package com.ktayl.core.screening.api;
