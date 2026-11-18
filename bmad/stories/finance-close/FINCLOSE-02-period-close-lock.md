---
id: FINCLOSE-02
title: "accounting-period close/reopen with an immutable closing audit"
status: Ready
type: Story
epic: finance-close
milestone: "FINCLOSE — Finance Close & Reconciliation v1"
estimate: 5
labels: [java, backend, finance, ktayl-core]
priority: P3
assignee: AndreLiar
repo: andrelair-platform/ktayl-core
project: 28
initiative: Insurance LOB
---

## Story

As a **finance officer**, I want to **close an accounting period** — locking it with a tamper-evident record
of who closed it and the figures attested — and to **reopen** it for a correction, so that the IS has an
auditable period close (the control an auditor asks for), not just a live reconciliation view.

## Background

FINCLOSE-01 built the `finance` module as a **read** layer: the GL reconciliation report + premium register,
plus the `finance.accounting_period` registry (V14) which is currently **read-only** (a period with no row is
implicitly `OPEN`). This story adds the **close action + its audit** on top of that registry, reusing
`FinanceCloseService.reconcile()` to compute the figures a close attests to.

## The binding architectural decision — close is a CONTROL, not a physical lock (ADR-019)

`finance` **must not** become a dependency of `billing`: `finance → billing` already exists (the
`ledger-view` port), so having `billing` consult finance's period status before posting would create a
module **cycle** and `ModularityTests` would (correctly) fail. Therefore the close **cannot** physically
block billing's outbox from posting. Instead:

- A close is an **attestation + lock on the finance side**: it records that, at close time, the period's
  subledger was settled and reconciled, and marks the period `CLOSED` so finance reads/reports treat it as
  final.
- Because FINCLOSE-01 attributes a posting to the period of its **`created_at`** (UTC), once a month has
  ended **no new posting can be attributed to it** — so "late postings into a closed period" is structurally
  near-impossible under our attribution rule. The residual risk is a posting *already attributed* to the
  period that was still `pending`/`failed` at close and later drains to the GL. The close **precondition
  eliminates that** (you may only close a fully-settled subledger), and a **post-close-movement detector**
  surfaces it defensively if it ever occurs.

This is how real close processes work (attest + detect + reopen-for-adjustment), and it keeps the module
boundary intact. The rejected alternative — a hard write-lock enforced in billing — is recorded in ADR-019.

## Acceptance Criteria

- [ ] **AC-1 — close a period.** `POST /api/finance/periods/{period}/close` closes an `OPEN` period, with
      **preconditions** (each a `409` with a clear reason, never a silent no-op):
      - the period has **ended** (`period.end ≤ now UTC`) — you cannot close the current or a future period;
      - the subledger is **settled** — **zero** `PENDING` and `FAILED` postings in the period (nothing can
        change post-close);
      - the GL **agrees** — if the GL is available and `driftMinor ≠ 0`, close is refused unless the request
        body carries `{ "force": true, "reason": "<non-blank>" }`; if the GL is `UNAVAILABLE`, close is
        allowed and the audit records `glStatus = UNAVAILABLE`.
      On success → the period becomes `CLOSED` and an **immutable closing-audit row** is appended (AC-2).
- [ ] **AC-2 — immutable closing audit.** Each close/reopen appends one **append-only** audit row:
      `period`, `action` (`CLOSED` | `REOPENED`), `actor` (from the JWT, never a placeholder), `at`, and —
      for a close — the **attested snapshot**: `subledgerPostedMinor`, `glPostedMinor` (nullable),
      `driftMinor`, `glStatus`, `forced`, `reason`. Rows are **never updated or deleted** (the snapshot is
      the evidence; later reads can't alter it).
- [ ] **AC-3 — reopen.** `POST /api/finance/periods/{period}/reopen` with `{ "reason": "<non-blank>" }`
      reopens a `CLOSED` period (→ `OPEN`) and appends a `REOPENED` audit row. Reopening an `OPEN` period →
      `409`; a missing/blank reason → `422`.
- [ ] **AC-4 — state guards.** Closing an already-`CLOSED` period → `409`. The lifecycle is exactly
      `OPEN ⇄ CLOSED` via these two audited transitions; no other transitions exist.
- [ ] **AC-5 — close history endpoint.** `GET /api/finance/periods/{period}/close` returns the ordered list
      of close/reopen audit entries for the period (empty list if never closed).
- [ ] **AC-6 — reconciliation reflects the close.** `PeriodReconciliation` already carries `periodStatus`;
      add `lastClosedAt` (nullable) and `postCloseMovement` (boolean): `true` iff any posting attributed to a
      `CLOSED` period has a `posted_at` **after** `lastClosedAt` — the defensive integrity signal that
      something moved after close (expected `false` given AC-1's settled precondition).
- [ ] **AC-7 — authority.** `close` and `reopen` require `hasAuthority('GROUP_Finance')` (401 no token / 403
      wrong group), like FINCLOSE-01. (Dual-control / a senior approver for close is noted as a revisit, not
      built now.)
- [ ] **AC-8 — money safety.** All attested amounts are `long` minor units (eurocents); the snapshot is
      captured at close and is independent of later reconciliation reads.

## Technical Notes

- **Reuse the reconciliation.** The close precondition + the attested snapshot come straight from
  `FinanceCloseService.reconcile(period)` (settled = `pendingCount + failedCount == 0`; `driftMinor`;
  `glStatus`). Don't recompute — call it, then decide. Put the pure decision (settled? ended? drift-forced?)
  in a `finance.domain` class (`PeriodCloseDecision`) so it's unit-tested without Spring.
- **V15 migration** (`V15__finance_period_close_audit.sql`, next after V14): an **append-only**
  `finance.period_close_audit` table (`id uuid pk`, `period varchar(7)`, `action varchar(16) CHECK (action IN
  ('CLOSED','REOPENED'))`, `actor`, `at timestamptz`, `subledger_posted_minor bigint`, `gl_posted_minor
  bigint NULL`, `drift_minor bigint`, `gl_status varchar(16)`, `forced boolean`, `reason varchar(500) NULL`,
  index on `(period, at)`). `accounting_period.status` (already in V14) holds the current state.
- **Ports (hexagonal).** Add a mutating `PeriodLifecyclePort` (`close`/`reopen` the registry row — upsert
  status) and a `ClosingAuditPort` (append + list) in `finance.domain`, with adapters in
  `finance.infrastructure.persistence`. `AccountingPeriodPort` (read) stays.
- **Billing port addition (AC-6).** Extend billing's `ledger-view` named interface with a small query for the
  post-close-movement check — e.g. `long countPostedAfter(YearMonth period, Instant closedAt)` over
  `ledger_outbox` (`posted_at > closedAt` within the period window). Keep the surface minimal; `finance`
  consumes it, never billing's tables. `ModularityTests` must stay green.
- **Exceptions → HTTP.** New `finance.domain` exceptions (`PeriodNotClosableException` → 409,
  `IllegalPeriodTransitionException` → 409, reuse/extend for 422 on missing reason) mapped in
  `FinanceExceptionHandler`.
- **Concurrency.** Close is a short transaction; guard the registry upsert so two concurrent closes don't both
  append `CLOSED` (optimistic check on current status within the tx).
- **No change to billing's posting path** — it stays oblivious to finance (the boundary decision above).

## Definition of Done

- [ ] Code implements AC-1..AC-8.
- [ ] **L0** — compile clean (`mvn -ntp verify`), 0 warnings-as-errors.
- [ ] **L1 unit** — `PeriodCloseDecision` (ended / settled / drift-forced / reason-required) + the reopen
      guards; ≥70% on new code; pure, no Spring.
- [ ] **L2 integration** — real Postgres: settle a period → close → assert status `CLOSED` + one immutable
      audit row with the attested snapshot; a period with a `FAILED` row → close `409`; reopen → `OPEN` +
      `REOPENED` row; double-close → `409`; `postCloseMovement` flips when a posting drains after close.
- [ ] **L3 web contract** — close `200/409` (not-ended / not-settled / drift-no-force / already-closed),
      `401/403`; reopen `200/409/422`; close-history `200`.
- [ ] `ModularityTests` + `HexagonalArchitectureTests` green.
- [ ] OpenAPI updated (`api/finance.openapi.yaml`: close / reopen / close-history + the new
      `PeriodReconciliation` fields).
- [ ] **ADR-019** — "the period close is a control/attestation + detection, not a physical lock on billing"
      (records the boundary rationale + the rejected hard-lock option).
- [ ] PR → `main`, `Closes …#<issue>`; ArgoCD Synced + Healthy on dev; V15 applies on boot.
- [ ] Live check on dev: settle a real period, close it via the API, re-read reconciliation → `CLOSED` +
      `lastClosedAt`, and the close-history endpoint returns the audit entry.

## Tasks

- [ ] TASK-1: billing `ledger-view` — add `countPostedAfter(YearMonth, Instant)`; unit-test it.
- [ ] TASK-2: `V15__finance_period_close_audit.sql` (append-only audit table).
- [ ] TASK-3: domain — `PeriodCloseDecision` (pure) + `PeriodLifecyclePort` + `ClosingAuditPort` + exceptions.
- [ ] TASK-4: application — `PeriodCloseService` (close/reopen) reusing `reconcile()` for figures + snapshot.
- [ ] TASK-5: infra persistence — audit entity/repo/adapter + the registry status-mutation adapter.
- [ ] TASK-6: web — close / reopen / close-history endpoints + exception mapping (409/422).
- [ ] TASK-7: extend `PeriodReconciliation` (`lastClosedAt`, `postCloseMovement`) + wire the movement check.
- [ ] TASK-8: L1/L2/L3 tests.
- [ ] TASK-9: ADR-019 + OpenAPI update.

## Dependencies

- Depends on: **FINCLOSE-01** (the `finance` module, the reconciliation it reuses, the `accounting_period`
  registry V14, billing's `ledger-view` port) — must be merged first.
- Blocks: a complete period-close story for the IS; any later close-dashboard / alerting (FINCLOSE-03) reads
  the `CLOSED` state + the audit.
