# ADR-018 — Finance close: a READ-layer reconciliation module (not a second ledger, not an event projection)

**Status:** Accepted (2026-11-18) · **Owner:** SA/TL · **Context:** FINCLOSE-01 builds the `finance` module —
the ktayl-core-module realisation of `ktayl-finance` FIN-05 (board #14). This ADR records *how* finance
relates to billing + the GL, so a later reader understands why it is a thin read layer and not a ledger.

## Context — the per-transaction GL posting is already DONE in billing
`billing` already posts balanced double-entry Journal Entries to ERPNext via a transactional outbox
(BILL-014, migration V8: `GlPostingListener` → `ledger_outbox` → `LedgerOutboxDrainer` → `ErpNextLedgerClient`,
idempotent by `(event_type, reference)`, offline-resilient). So `premium→cash→GL` posting is solved. What the
IS still lacks is the **period-end** view: *does the GL agree with the billing subledger this month, what is
stuck, and what is the premium register?* That is the gap `finance` fills.

## Decision
Build `finance` as a **read-layer close module**: it **reconciles** billing's subledger against ERPNext and
produces a **premium register**, over a period. It is **not** a second ledger and does **not** re-post to the
GL.

1. **Consume billing via a published named interface, never its tables (ADR-006).** Billing exposes a new
   `ledger-view` Spring Modulith named interface (`LedgerReconciliationView` + `LedgerReconciliationSummary`)
   — an aggregate over `ledger_outbox`. `finance` depends on that port only; `ModularityTests` enforces it.
2. **Pull, don't project.** The reconciliation is a *query* over billing's view + an ERPNext read — **not** an
   event-sourced period projection that duplicates the subledger billing already owns. (Option B below.)
3. **Period attribution = `created_at` UTC → `YYYY-MM`** (FINCLOSE-01 AC-2). The outbox carries no separate
   business date; joining to the invoice/payment business date would breach the boundary. Recorded-date
   convention; a distinct accounting-date column is a later concern.
4. **ERPNext is the GL system of record** — `finance` only *reads* it (a read adapter, gated on the same
   `ledger.erpnext.api-key` as billing's writer). GL unreachable/unconfigured → the reconciliation reports
   `UNAVAILABLE`, never a 5xx; the subledger view is always available.
5. **Single-currency (EUR) guard.** The IS is EUR today; `finance` asserts EUR and surfaces any non-EUR
   posting as a reconciliation exception rather than cross-summing currencies. Multi-currency is a non-goal.

### Options considered
- **A — read-layer reconciliation via billing's port + ERPNext read** *(chosen)*. Minimal surface, no second
  ledger, honours the module boundary; the two numbers a reviewer wants (drift, stuck-count) fall straight out.
- **B — event-sourced period projection** (finance listens to `PremiumInvoiced`/`PaymentCaptured` and
  maintains its own period ledger). *Rejected:* duplicates the subledger billing already owns, muddies the
  boundary (finance would shadow billing's source of truth), and adds eventual-consistency drift between two
  copies — all to answer a question a pull already answers.
- **C — reach into `ledger_outbox` directly from finance.** *Rejected:* a straight ADR-006 schema-ownership
  violation; `ModularityTests` would (correctly) fail.

## Scope boundary — deferred (stays on ktayl-finance #14)
- **FINCLOSE-02** — accounting-period **lifecycle + close** (open/closed, lock a period with a closing audit,
  park postings targeting a closed period). The `finance.accounting_period` registry (V14) is introduced now
  but FINCLOSE-01 only **reads** it (a period with no row is implicitly OPEN).
- **FIN-03 reserving** (case + IBNR → GL provisions — needs claims reserve data + an actuarial model) and
  **FIN-02 IFRS 17** (CSM/risk-adjustment — large; likely adopt, not build). Neither is in this module yet.

## Known assumption (v1)
The ERPNext GL read sums *all* submitted Journal Entries for the company in the period, assuming **billing is
the only poster** to that ledger — true in the IS today. When another poster is added, the read must filter to
billing's JEs (e.g. by the `cheque_no` idempotency-ref). Reconciling drift is exactly what would surface a
violation of this assumption.

## Consequences
- **Positive:** completes the money loop with an **auditable close** (drift + premium register) reusing
  billing's patterns; no new vendor, no actuarial modelling, no second ledger; a clean, tooling-enforced
  module boundary (finance → billing `ledger-view` only). Concrete BC02/BC03 cert evidence (a traceable
  financial control).
- **Negative / accepted:** period attribution is recorded-date, not a separately-tracked accounting date
  (good enough until FINCLOSE-02); the GL read's "billing is the only poster" assumption must be revisited
  when it stops holding; single-currency only.

Related: ADR-002 (ERPNext GL / outbox), ADR-004 (modular-monolith target), ADR-005/006 (bounded contexts +
schema ownership — the named-interface rule this applies), ADR-013 (hexagonal). Story: `bmad/stories/finance-close/`.
