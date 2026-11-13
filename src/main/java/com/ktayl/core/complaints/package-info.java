/**
 * Complaints &amp; Disputes module — register → acknowledge → assign → resolve → close a complaint
 * (an ACPR/EIOPA/IDD regulatory obligation). The Phase-3 greenfield vertical slice that proves the
 * ktayl-core modular-monolith pattern end-to-end (SPEC: {@code docs/specs/spec-complaints/SPEC.md}).
 *
 * <p>A CLOSED Spring Modulith module: other modules may use only its published API ({@code api}) and
 * events ({@code events}), never its {@code internal} types; it owns the {@code complaints} Postgres
 * schema and may not read another module's tables (ADR-005/006). Cross-module interaction is a published
 * interface or an in-process application event — never NATS between modules (ADR-007).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Complaints")
package com.ktayl.core.complaints;
