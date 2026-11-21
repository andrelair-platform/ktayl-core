/**
 * Claims GL module — the thin, one-way <b>general-ledger posting</b> seam for claims financials (ADR-021).
 * It consumes claim financial <b>events</b> ({@code insurance.claims.financial}: a reserve movement or an
 * executed loss payment) off NATS JetStream and posts balanced double-entry Journal Entries to the ERPNext
 * GL via a transactional outbox — exactly as {@code billing} does for premiums (BILL-014).
 *
 * <p><b>It is NOT the claims financial workflow.</b> Reserve set/adjust/approve, payment
 * request→approve→execute and large-loss review stay in {@code ktayl-claims} (which owns the claim, over the
 * GlobalCore legacy). This module only turns a claim financial fact into a ledger entry — one way, async,
 * never calling back into claims (no distributed-monolith chain). It is also NOT the ADR-014 fold.
 *
 * <p>A CLOSED Spring Modulith module: other modules may use only its published API / events, never its
 * internals; it owns the {@code claimsgl} Postgres schema and may not read another module's tables (ADR-006).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Claims GL")
package com.ktayl.core.claimsgl;
