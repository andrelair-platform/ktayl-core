# ADR-021 — Claims GL seam: a thin, one-way GL-posting module in ktayl-core (workflow stays in ktayl-claims)

**Status:** Accepted (2026-11-21) · **Owner:** SA/TL · **Context:** the IS needs claims **settlement +
reserving** financials — reserve set/adjust/approve, payment request→approve→execute, large-loss review, and
**GL posting** (reserve movements → technical provisions; loss payments → claims-paid/cash). `ktayl-claims`
today does FNOL/intake + a reserve/settle **state machine over the frozen Oracle GlobalCore legacy** (ACL +
CDC read-model, ADR-claims-001..008); it has **no payment workflow and no GL posting**. This ADR records
*where* that financial capability lives and *how* the first slice (the GL seam) is built.

## Decision
Split the capability along the ownership seam, and build the **GL posting** as a thin, one-way module in
`ktayl-core` — **not** the whole financial workflow.

1. **The financial _workflow_ stays in `ktayl-claims`** — reserve set/adjust/**approve**, payment
   request→approve→execute, large-loss review. It extends the authority model `ktayl-claims` already has
   (`X-Claims-Authority`, above-authority ⇒ 403) and keeps the **claim whole** (state + money in one place,
   over the legacy SoR). This is a later `ktayl-claims` slice, not this one.
2. **`ktayl-core` owns ONLY the GL posting** — a new **`claimsgl`** module that consumes claim financial
   **events** and posts balanced Journal Entries to ERPNext. This is exactly what `ktayl-core` already does
   for premiums (billing's outbox→ERPNext, BILL-014); the GL + ERPNext integration is core's lane.
3. **One-way, async, event-driven — never a synchronous call chain.** `claimsgl` **consumes** a published
   claim financial event off NATS JetStream and never calls back into claims. No `claim → core → claim`
   request path (the distributed-monolith anti-pattern). This mirrors billing consuming Underwriting's
   `bound-risk` (ADR-003) — core reads the event, never claims' tables or its legacy.
4. **This is NOT the ADR-014 fold.** ADR-014 folds claims into core *post-legacy-strangler*; that is not
   done. `claimsgl` is a *GL-posting seam fed by events*, leaving `ktayl-claims` intact and **more** capable
   (it gains the approval/payment workflow). The fold remains a separate, later, evidence-gated decision.

### The event contract (the consumer's pinned view)
`claimsgl` defines `ClaimFinancialEvent` as **its** view of what claims emits (like billing's
`BoundRiskEvent` pins UW's payload), subject `insurance.claims.financial`:
```
{ event_id, type: "reserve-set"|"payment-executed", claim_number, policy_number,
  amount_minor, currency, occurred_on }
```
- **reserve-set** — `amount_minor` is the reserve **movement** (signed: +increase / −downward adjustment).
- **payment-executed** — `amount_minor` is the loss payment (> 0).
Idempotency/JE-remark key = `event_id` (stable per movement; NATS is at-least-once → the outbox
`UNIQUE(reference)` dedupes a redelivery). **`ktayl-claims` does not emit this yet** — slice 1 proves the
seam with a synthetic publish (the way billing's chain was first proven); claims emits it in its workflow slice.

### GL mapping (config-driven French-PCG accounts)
| Event | Journal Entry |
|---|---|
| reserve-set, movement m > 0 | DR *Claims incurred* (expense) / CR *Outstanding claims provision* (liability) |
| reserve-set, movement m < 0 | the reverse (DR provision / CR incurred), `|m|` |
| payment-executed, amount a | DR *Outstanding claims provision* / CR *Cash* |
Accounts come from config (`claims.ledger.accounts.{incurred,provision,cash}`) → mapped to the live CoA
without a code change. Balance (Σdr = Σcr) enforced at `JournalEntry` construction (an unbalanced JE can't
be built). Money is integer eurocents throughout.

### Implementation shape (slice 1 — mirrors BILL-014, self-contained)
`claimsgl` is a CLOSED module owning the **`claimsgl`** Postgres schema. Durable JetStream consumer →
**transactional outbox** (`claim_ledger_outbox`, idempotent by `event_id`) written in one tx → a `@Scheduled`
drainer posts `pending` rows to ERPNext as balanced JEs (idempotent by the outbox id = JE `cheque_no`), a GL
outage leaves rows `pending`→retried (never blocks). The ERPNext client is gated on the same
`ledger.erpnext.api-key` property as billing's (dev blank → outbox accumulates, no ERPNext call).

### Options considered
- **A — GL-only seam in core, workflow in claims, async one-way events** *(chosen)*. Claims stays whole and
  gains the workflow; core does only what it's best at (ERPNext GL). No split-brain, no fold, clean boundary.
- **B — full claims-finance (workflow + GL) in a core module; claims = state SoR only.** *Rejected:* splits a
  claim's money from its state across two services (reserve in claims, reserve-*approve* in core → chatty
  cross-service calls per movement), and is the thin end of the ADR-014 fold done early/through the side door.
- **C — extend `ktayl-claims` to also post the GL.** *Rejected:* layers greenfield money+GL+four-eyes onto a
  Strangler-over-Oracle service whose write model is the legacy DB (CQRS-lite, no Postgres write-model), and
  duplicates the ERPNext integration + outbox that already exist in core. Core is the GL home.
- **D — fold claims fully into core now (ADR-014 end-state).** *Rejected here:* large, and contradicts
  ADR-014's own *post-legacy-strangler* sequencing. Left as the separate, later, gated decision.

## Consequences
- **Good:** claims-the-service is reinforced, not obsoleted; the boundary is a published event (stable, like
  `bound-risk`); core reuses its proven outbox→ERPNext pattern; technical-provision + claims-paid accounting
  becomes real GL evidence (Solvency II / IFRS 17 claims reserves).
- **Watch-outs / accepted cost:** `claimsgl` **duplicates** billing's ledger mechanics (`JournalEntry` VO +
  the ERPNext client + the outbox/drainer/posting trio). Per `clean-code.md` WET-first, duplication is
  sanctioned on the **2nd** occurrence; **extract a shared `ledger` capability on the 3rd GL consumer** —
  recorded in `deferred-work.md`, not done now (premature extraction would couple the modules or widen
  `shared`). Two ERPNext client beans (billing + claimsgl) to the same ERPNext is fine (each module-owned).
- **Enforced by:** `ModularityTests` (claimsgl self-contained, no reach into billing's ledger) +
  `ClaimJournalEntryFactoryTest` (the 3 mappings + balance) + `ClaimFinancialEventContractTest` (the wire
  shape) + (slice-2) an L2 publish→outbox→post integration test + the live GL proof (synthetic publish).

## Slice plan (the epic)
- **CLM-FIN-01 (this)** — the `claimsgl` GL seam: event contract + durable consumer + outbox + ERPNext JE
  (reserve-movement + payment), proven with a synthetic publish.
- **CLM-FIN-02** — `ktayl-claims`: reserve set/adjust/**approve** + the `insurance.claims.financial` emitter.
- **CLM-FIN-03** — `ktayl-claims`: payment request→approve→**execute** (four-eyes + large-loss authority gate).
- **CLM-FIN-04** — large-loss review board + reporting; wire the claims GL into the finance close
  reconciliation (FINCLOSE) alongside premiums.
