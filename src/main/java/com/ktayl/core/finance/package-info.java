/**
 * Finance module — the period-end <b>close</b> layer: reconcile what {@code billing} posted to the general
 * ledger against the GL (ERPNext), produce a per-period premium register / trial balance, and (FINCLOSE-02+)
 * close a period. The ktayl-core-module realisation of {@code ktayl-finance} FIN-05 (board #14); the
 * per-transaction {@code premium→cash→GL} posting is already done in {@code billing}, so this is a READ layer
 * on top, never a second ledger.
 *
 * <p>A CLOSED Spring Modulith module: other modules may use only its published API ({@code api}); it owns the
 * {@code finance} Postgres schema and may not read another module's tables (ADR-006). It depends on
 * {@code billing} solely through billing's published {@code ledger-view} named interface — never billing's
 * internals or the {@code ledger_outbox} table.
 *
 * <p>Internally it is <b>hexagonal</b> (ports &amp; adapters, ADR-013), dependencies pointing inward:
 * {@code domain} is pure logic + the outbound ports (the GL read + the period registry), {@code application}
 * holds the use cases (wiring billing's view + the ports, mapping to the api DTOs), and {@code infrastructure}
 * holds the adapters ({@code persistence} = the period registry, {@code gl} = the ERPNext read, {@code web} =
 * REST).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Finance")
package com.ktayl.core.finance;
