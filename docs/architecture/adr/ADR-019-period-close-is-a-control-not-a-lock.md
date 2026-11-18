# ADR-019 — The period close is a control/attestation + detection, NOT a physical lock on billing

**Status:** Accepted (2026-11-18) · **Owner:** SA/TL · **Context:** FINCLOSE-02 adds the accounting-period
**close** on top of the FINCLOSE-01 `finance` module. This ADR records *why* the close is an attestation +
detection rather than a hard write-lock that stops billing from posting into a closed period.

## Context
A finance "period close" conventionally means: after month-end, lock the period so the books are final. The
naive implementation is a **hard lock** — billing consults the period status before posting and refuses a
posting into a closed period. On this platform that is **architecturally impossible without breaking the
module boundary**:

- `finance → billing` already exists (finance consumes billing's `ledger-view` named interface).
- A hard lock needs `billing → finance` (billing asking finance "is this period closed?").
- That makes a **module dependency cycle**, which `ModularityTests` (`ApplicationModules.verify()`) rejects
  by design (ADR-004/005/006). Billing must stay oblivious to finance.

There is also a **structural fact** that makes a hard lock largely unnecessary: FINCLOSE-01 attributes a
posting to the period of its `created_at` (UTC). Once a month has ended, **no new posting can be attributed
to it** (new rows carry a later `created_at`). The only residual case is a posting *already attributed* to the
period that was still pending/failed at close and later drains to the GL.

## Decision
The close is a **control / attestation on the finance side, plus detection** — never a physical lock on
billing.

1. **Attestation.** A close records that, at close time, the period's subledger was **settled** (no
   pending/failed postings) and **reconciled** (GL drift 0, or an explicit `force` + reason), and marks the
   period `CLOSED`. An immutable audit row captures who/when + the attested figures.
2. **Precondition eliminates the residual case.** You may only close a **fully-settled** period — so there are
   no pending/failed rows left that could move to the GL after close. This is what makes the close meaningful.
3. **Detection, not prevention.** The reconciliation exposes `postCloseMovement` — `true` iff a posting
   attributed to a CLOSED period reached the GL after `lastClosedAt`. Expected `false` (precondition #2); if it
   ever flips, finance **surfaces** it for review (reopen → adjust → re-close), the way real close processes
   handle a post-close entry.
4. **Billing is untouched.** Billing's posting path has no knowledge of finance; the boundary stays acyclic.

### Options considered
- **A — control/attestation + detection** *(chosen)*. Respects the module boundary, matches how real finance
  closes work (attest + detect + reopen-for-adjustment), and the `created_at` attribution + settled
  precondition make a hard lock redundant.
- **B — hard write-lock in billing** (billing checks period status before posting). *Rejected:* creates the
  `billing → finance` cycle (ModularityTests fails), couples billing to a finance concern, and buys almost
  nothing given the attribution rule. The "lock" illusion would also be false for an async drainer mid-flight.
- **C — a shared "period status" module both depend on.** *Rejected now:* over-engineering for one consumer;
  reintroduces a shared-mutable-state coupling. Revisit only if multiple modules ever need to gate on period
  status.

## Consequences
- **Positive:** the module graph stays acyclic (tooling-enforced); the close is a genuine, auditable control
  with an immutable attestation; post-close anomalies are detected, not silently allowed; billing stays simple.
  Honest about what a close can and cannot guarantee on an async-posting system.
- **Negative / accepted:** finance cannot *physically* prevent a late posting — it can only require a settled
  subledger at close and detect movement after. Accepted: it's the correct trade for an intact boundary, and
  the precondition makes the residual case not occur in practice. A reopen is the sanctioned path for a genuine
  post-close adjustment.

Related: ADR-004/005/006 (modular monolith + boundaries + schema ownership — the cycle rule this honours),
ADR-018 (the finance close module: read-layer, not a second ledger), FINCLOSE-01 `created_at` attribution.
Story: `bmad/stories/finance-close/FINCLOSE-02-period-close-lock.md`.
