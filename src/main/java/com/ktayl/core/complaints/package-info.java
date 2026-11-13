/**
 * Complaints &amp; Disputes module — register → acknowledge → assign → resolve → close a complaint
 * (an ACPR/EIOPA/IDD regulatory obligation). The Phase-3 greenfield vertical slice that proves the
 * ktayl-core modular-monolith pattern end-to-end (SPEC: {@code docs/specs/spec-complaints/SPEC.md}).
 *
 * <p>A CLOSED Spring Modulith module: other modules may use only its published API ({@code api}) and
 * events ({@code events}), never its internal {@code domain}/{@code application}/{@code infrastructure}
 * types; it owns the {@code complaints} Postgres schema and may not read another module's tables
 * (ADR-005/006). Cross-module interaction is a published interface or an in-process application event —
 * never NATS between modules (ADR-007).
 *
 * <p>Internally it is <b>hexagonal</b> (ports &amp; adapters, ADR-013), dependencies pointing inward only:
 * {@code domain} is PURE business logic (the {@code Complaint} aggregate + its outbound ports, zero
 * framework), {@code application} holds the use cases (Spring wiring, no business rules), and
 * {@code infrastructure} holds the adapters ({@code persistence} = JPA, {@code web} = REST). The tech is
 * swappable without touching the domain — the business logic survives it.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Complaints")
package com.ktayl.core.complaints;
