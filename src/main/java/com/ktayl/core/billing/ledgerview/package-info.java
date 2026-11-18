/**
 * The billing module's second PUBLISHED surface (alongside its events) — a read-only <b>ledger
 * reconciliation view</b> over the GL outbox, consumed by the {@code finance} module's period close
 * (FINCLOSE-01). A Spring Modulith {@link org.springframework.modulith.NamedInterface named interface}, so
 * the boundary is enforced by tooling: {@code finance} depends on
 * {@link com.ktayl.core.billing.ledgerview.LedgerReconciliationView} +
 * {@link com.ktayl.core.billing.ledgerview.LedgerReconciliationSummary} only — never billing's internal
 * {@code persistence}/{@code ledger} types or the {@code ledger_outbox} table (ADR-006 schema-per-module).
 *
 * <p>The surface is intentionally tiny and expressed in java/primitive types + an api-local DTO: a view
 * status abstraction ({@code POSTED}/{@code PENDING}/{@code FAILED}) decoupled from billing's raw DB status,
 * so the outbox can evolve without breaking the consumer. The implementation stays internal to billing.
 */
@org.springframework.modulith.NamedInterface("ledger-view")
package com.ktayl.core.billing.ledgerview;
