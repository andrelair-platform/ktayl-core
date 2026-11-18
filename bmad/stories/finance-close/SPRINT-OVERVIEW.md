# ktayl-core — Finance Close & Reconciliation (module backlog)

**Board:** Project #28 (ktayl-core — Insurance LOB modular monolith) · **Initiative:** Insurance LOB
**Home repo:** `ktayl-core` · **Module:** `finance` (new) · **Milestone:** `FINCLOSE — Finance Close & Reconciliation v1`

## Why this lives here, not in `ktayl-finance`

The `ktayl-finance` scaffold (board #14) holds the **business scope** (FIN-01..05). Per the SI-consolidation
decision (2026-11-13, `github-projects.md` + `architecture-strategy.md`), the **build target is a module
inside `ktayl-core`**, not a standalone service. This backlog is the ktayl-core-module realisation of
`ktayl-finance`'s **FIN-05 — Financial close / GL integration**.

## Scope boundary — what is already DONE (do not rebuild)

**FIN-01 premium billing + per-transaction GL posting is already LIVE in the `billing` module:**

- `billing.invoicing` raises `PremiumInvoiced`; `billing.payment` raises `PaymentCaptured`.
- `billing.ledger` (`GlPostingListener` → `ledger_outbox` table, migration V8 → `LedgerOutboxDrainer` →
  `ErpNextLedgerClient`) posts **balanced double-entry Journal Entries** to ERPNext, idempotently by
  `(event_type, reference)`, offline-resilient (a GL outage leaves rows `pending`, never blocks billing).

So per-transaction `premium→cash→GL` posting is **solved**. The `finance` module is the **period-end layer
on top**: reconciliation, a period register / trial balance, and the period close — the *auditable close* an
auditor or interviewer actually asks for.

## Epics / stories

| ID | Title | Kind | Priority | Status |
|---|---|---|---|---|
| FINCLOSE-00 | EPIC: Finance close & GL reconciliation (`finance` module) | Epic | P2 | Ready |
| FINCLOSE-01 | `finance` module scaffold + GL reconciliation report & premium register | Story | P2 | Ready |
| FINCLOSE-02 | Accounting-period lifecycle + period close (lock + closing audit) | Story | P3 | Draft (decompose) |
| FINCLOSE-03 | Close-dashboard read API + outbox-drift alerting | Story | P3 | Draft (decompose) |

**Explicitly deferred (not this sprint):** FIN-02 IFRS 17 (large — likely adopt, not build), FIN-03
actuarial reserving (needs claims reserve data + actuarial model), FIN-04 reinsurance bordereaux (waits on
the reinsurance module). These stay on `ktayl-finance` #14 as business scope.

## Modular-monolith boundary (MANDATORY)

The `finance` module **must not** read `billing`'s tables or internal types. It consumes `billing`'s `api/`
`@NamedInterface`. FINCLOSE-01's first task adds a read-only **ledger reconciliation summary** to billing's
`api` port; `finance` depends on that port only. This keeps the Spring Modulith boundary intact (verified by
the existing `ModularityTests`).
