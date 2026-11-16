/**
 * Screening module (RC-02) — AML / sanctions screening of a party at a business gate (intake / bind /
 * payment), an LCB-FT / DORA obligation. A party is checked against an external engine (yente / OpenSanctions
 * in S1); any match (or an engine outage → <b>fail-closed HOLD</b>) raises alerts that a human compliance
 * officer decides (HITL — the module never auto-clears). Every action writes an append-only audit row.
 *
 * <p>A CLOSED Spring Modulith module: other modules may use only its published {@code api}
 * ({@link com.ktayl.core.screening.api.ScreeningGate}) — which policy/billing will call in-process at the
 * gate in S2 — never its internal {@code domain}/{@code application}/{@code infrastructure}; it owns the
 * {@code screening} Postgres schema and may not read another module's tables (ADR-005/006).
 *
 * <p>Internally it is <b>hexagonal</b> (ports &amp; adapters, ADR-013), dependencies pointing inward only:
 * {@code domain} is PURE business logic (the ALLOW/HOLD rule + its outbound ports, zero framework),
 * {@code application} holds the use cases (Spring wiring, no rules), and {@code infrastructure} holds the
 * adapters ({@code engine} = the yente RestClient, {@code persistence} = JPA, {@code web} = REST). The tech
 * is swappable without touching the domain.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Screening")
package com.ktayl.core.screening;
