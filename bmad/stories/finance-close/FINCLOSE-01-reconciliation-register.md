---
id: FINCLOSE-01
title: "finance module scaffold + GL reconciliation report & premium register"
status: Ready
type: Story
epic: finance-close
milestone: "FINCLOSE — Finance Close & Reconciliation v1"
estimate: 5
labels: [java, backend, finance, ktayl-core]
priority: P2
assignee: AndreLiar
repo: andrelair-platform/ktayl-core
project: 28
initiative: Insurance LOB
---

## Story

As a **finance officer**, I want a **per-period GL reconciliation report and premium register** so that I
can see — in one place — that the general ledger (ERPNext) agrees with what billing invoiced and collected,
and so that any unposted or failed GL entries are visible instead of silently stuck.

## Background

`billing` already posts balanced Journal Entries to ERPNext via the `ledger_outbox` (migration V8):
`PREMIUM_INVOICED` → *DR Premium Receivable / CR Premium Income*, `PAYMENT_CAPTURED` → *DR Cash / CR Premium
Receivable*. Postings are idempotent by `(event_type, reference)` and drained async, so a GL blip leaves a
row `pending`. Today **nothing surfaces the period-level picture**: the subledger total, the ERPNext total,
the drift between them, and the rows that never posted.

This story introduces the `finance` module and its first capability — a **read-only reconciliation report**
plus a **premium register** for an accounting period (e.g. `2026-10`). No period *locking* yet (that is
FINCLOSE-02); this story is the close *report*, not the close *action*.

## Scope

**In:** the `finance` module skeleton; a billing `api` addition exposing a ledger reconciliation summary; the
reconciliation report + premium register for a period; one SSO-gated read endpoint; the test layers below.

**Out:** period open/close/lock (FINCLOSE-02); dashboards + alerting (FINCLOSE-03); reserving / IFRS 17 /
bordereaux (deferred to #14); any *write* to ERPNext (finance only reads the GL here).

## Acceptance Criteria

- [ ] **AC-1 — billing api port (no boundary breach).** `billing` exposes, via its `api/` `@NamedInterface`,
      a read-only ledger reconciliation summary for a period — e.g.
      `LedgerReconciliationSummary byPeriod(YearMonth period)` returning, per `event_type` (`PREMIUM_INVOICED`,
      `PAYMENT_CAPTURED`): counts and `amount_minor` totals split by status (`pending` / `posted`), sourced
      from `ledger_outbox`. `finance` depends ONLY on this port — it never imports billing persistence/internal
      types. `ModularityTests` stays green.
- [ ] **AC-2 — period attribution (DECIDED).** Each outbox row is attributed to an accounting period by its
      **`ledger_outbox.created_at` → `YearMonth` (UTC)** — i.e. the date the billing fact was recorded is the
      accounting date. Rationale: `ledger_outbox` carries no separate business date, and joining back to the
      invoice/payment business date would reach into billing internals and breach the Modulith boundary. This
      is the recorded-date convention; a distinct accounting-date column is a FINCLOSE-02 concern if ever
      needed. The rule is a pure function (`UTC created_at → YearMonth`) and is unit-tested, including the
      month-boundary edge (23:59:59Z on the last day vs 00:00:00Z on the first).
- [ ] **AC-3 — GL-side total.** `finance` reads the **posted** total from ERPNext for the period (sum of the
      Journal Entries billing created, matched by the `posted_ref` / remark convention) via a read-only GL
      client, and compares it to the subledger `posted` total. If ERPNext is unreachable, the report returns
      the subledger view with the GL side marked `unavailable` (never a 500).
- [ ] **AC-4 — reconciliation report.** `GET /finance/periods/{period}/reconciliation` (period = `YYYY-MM`)
      returns: subledger posted total, GL posted total, **drift** (minor units; `0` = reconciled), and the
      count + total of `pending` and `failed` (`attempts > 0`, still `pending`) rows — i.e. the "stuck" GL
      entries. Drift and stuck-count are the two numbers a reviewer reads first.
- [ ] **AC-5 — premium register.** `GET /finance/periods/{period}/register` returns the period premium
      register: gross premium invoiced, cash received, receivable outstanding (invoiced − received), each in
      minor units, derived from the billing summary. Totals tie out (received ≤ invoiced; outstanding =
      invoiced − received).
- [ ] **AC-6 — authority.** Both endpoints require `hasAuthority('GROUP_Finance')` (per the module
      group→authority pattern in `shared/config/SecurityConfig`); an unauthenticated or wrong-group caller
      gets `401` / `403`, verified by a slice test.
- [ ] **AC-7 — money safety.** All amounts are `long` minor units (eurocents); no floating point anywhere in
      the module. A deliberate unit test asserts the register/drift math on a mixed-currency-free dataset.

## Technical Notes

- **Package layout (mirror `complaints`):** `com.ktayl.core.finance` with `api/` (`@NamedInterface` —
      `FinanceCloseOperations` + DTOs `PeriodReconciliation`, `PremiumRegister`; never leak internal types),
      `domain/` (the reconciliation/register domain logic — pure, unit-testable), `application/` (the service
      wiring billing's port + the GL read client), `infrastructure/web/` (`FinanceController`,
      `@RestControllerAdvice` mapping).
- **Billing api addition (AC-1):** add the summary query to billing's existing `api` package next to its
      current operations. Keep it a **projection/DTO**, not an entity — the outbox row type stays internal to
      billing. This is the only change to `billing` in this story; keep it minimal.
- **GL read client (AC-3):** reuse the ERPNext access pattern from `billing.ledger.ErpNextLedgerClient` (same
      base URL / auth via config + ESO-delivered secret) but **read-only** — a `ResourceLoader`-style query of
      Journal Entries for the period. If no GL client is configured (same guard as `LedgerOutboxDrainer`'s
      `ledgerAvailable()`), the GL side is `unavailable`, not an error.
- **Migration V14 (`V14__finance_close.sql`):** this story is largely a **read model over billing + ERPNext**,
      so keep the schema minimal. Introduce the `accounting_period` registry table (period `YYYY-MM`, status
      default `open`, created_at) so FINCLOSE-02 has a home for the lock; FINCLOSE-01 only ever reads/implies
      `open`. Do **not** duplicate billing's outbox data into finance (no second ledger).
- **No new NATS/event wiring** — the reconciliation is a *pull* over billing's api port + ERPNext, not an
      event projection. (An event-sourced period projection was considered and rejected: it would duplicate
      the subledger billing already owns and muddy the boundary. Note this rationale as an ADR stub.)
- **Spring Modulith:** add the `finance` module to the application-module structure; run `ModularityTests`
      (or `ApplicationModules.verify()`) — a boundary breach fails the build, which is the point.
- **Currency (DECIDED — EUR-only guard).** The IS is single-currency (EUR) today. The billing summary groups
      totals by `currency`; `finance` asserts **EUR** and does **not** cross-sum currencies. Any non-EUR outbox
      row is surfaced as a **reconciliation exception** on the report (a non-empty `unexpectedCurrencies` list +
      the offending references), never silently added to or omitted from the EUR totals. Multi-currency
      reconciliation (FX-rated register, per-currency ledgers) is an explicit non-goal for v1 and recorded as a
      known limitation in the ADR stub.

## Definition of Done

- [ ] Code implements AC-1..AC-7.
- [ ] **L0** — `./mvnw spotless:check` (or the repo's formatter) + compile clean, 0 warnings-as-errors.
- [ ] **L1 unit** — reconciliation drift + register math (AC-5, AC-7) + period attribution (AC-2); ≥70% on new
      code. Pure-domain tests, no Spring context.
- [ ] **L2 integration** — real Postgres (testcontainers/service-container): seed `ledger_outbox` rows across
      statuses + periods, assert the billing summary port and the finance report agree end-to-end; ERPNext/GL
      client **faked** at its boundary (an in-test stub returning a known posted total) — the fake is backed by
      AC-3's contract so the mock encodes a real assumption (`testing.md` mock-discipline).
- [ ] **L3 contract** — the two `finance` endpoints validated against a committed OpenAPI fragment; the billing
      `api` port has a consumer-side test so a future billing change that breaks the summary shape fails here.
- [ ] `ModularityTests` green (clean `finance` ↔ `billing` boundary).
- [ ] OpenAPI updated for the two endpoints (`documentation.md` endpoint rule).
- [ ] PR → `main`, linked `Closes andrelair-platform/ktayl-core#<issue>`; ArgoCD **Synced + Healthy** on dev
      (`ktayl-core` ns); V14 applies on boot (Flyway).
- [ ] QA/live check: hit `/finance/periods/{period}/reconciliation` on dev against a period that has real
      billing postings → drift + stuck-count are correct. (Full L5 QA gate applies only at prod promotion.)

## Tasks

- [ ] TASK-1: Add `LedgerReconciliationSummary byPeriod(YearMonth)` to billing's `api/` package (projection
      over `ledger_outbox`); unit-test the summary. **Note:** billing's `api/` currently has no
      `@NamedInterface` `package-info.java` (unlike complaints/policy/submission/screening) — add one so the
      summary is an explicit exposed port and the finance→billing dependency is a sanctioned named-interface
      edge, not a reach into billing's default package.
- [ ] TASK-2: Scaffold the `finance` module (api/domain/application/infrastructure) + register it as an
      application module; `ModularityTests` passing with the new empty module.
- [ ] TASK-3: `V14__finance_close.sql` — `accounting_period` registry table.
- [ ] TASK-4: Read-only ERPNext GL client for period posted-total (AC-3), with the `unavailable` guard.
- [ ] TASK-5: Reconciliation + register domain + service (AC-2, AC-4, AC-5, AC-7).
- [ ] TASK-6: `FinanceController` + `GROUP_Finance` authority + exception mapping (AC-6) + OpenAPI fragment.
- [ ] TASK-7: L1/L2/L3 tests; wire the GROUP_Finance authority mapping in `shared/config/SecurityConfig`.
- [ ] TASK-8: ADR stub `docs/adr/` — "finance reconciles via billing's api port + ERPNext read (not an event
      projection)"; note the single-currency limitation.

## Dependencies

- Depends on: `billing` module `api/` (extended in TASK-1), ERPNext GL read access (config + ESO secret —
  reuse billing's).
- Blocks: FINCLOSE-02 (period lock builds on the `accounting_period` table + the reconciliation report).
