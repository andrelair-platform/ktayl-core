/**
 * Submission Hub module (SUB-01, board #27) — the corporate broker-submission front door. It captures a
 * submission + its <em>exposure dossier</em> (sites / values / loss-history / geo-CAT), triages it for
 * completeness + appetite, and routes a qualified one onward to underwriting (domain #12). The holder is
 * sanctions-screened at intake; a HOLD flags the submission (it is still captured, but cannot be routed
 * until a compliance officer clears it — HITL).
 *
 * <p>A CLOSED Spring Modulith module: other modules may use only its published API ({@code api}), never its
 * internal {@code domain}/{@code application}/{@code infrastructure} types; it owns the {@code submission}
 * Postgres schema and may not read another module's tables (ADR-005/006). It depends on exactly one other
 * module — the {@code screening} module's published {@code api} ({@link com.ktayl.core.screening.api.ScreeningGate})
 * — at intake; cross-module interaction is a published interface, never NATS between modules (ADR-007).
 *
 * <p>Internally it is <b>hexagonal</b> (ports &amp; adapters, ADR-013), dependencies pointing inward only:
 * {@code domain} is PURE business logic (the {@code Submission} aggregate + its state machine + outbound
 * ports, zero framework), {@code application} holds the use cases (Spring wiring, no business rules), and
 * {@code infrastructure} holds the adapters ({@code persistence} = JPA, {@code web} = REST). The tech is
 * swappable without touching the domain — the business logic survives it.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Submission")
package com.ktayl.core.submission;
