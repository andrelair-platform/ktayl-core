# Case Study — Claims settlement, reserving & GL with four-eyes controls (CLM-FIN)

> Turning a claim reserve/payment into a governed, double-entry GL posting across a service boundary —
> the workflow kept in the claims Strangler, the GL posting in the modular monolith, joined by one async
> event. Decision of record: ADR-021. Proven live on prod with a real reserve through the Oracle legacy.

## 1. Business problem
`ktayl-claims` did FNOL + a reserve/settle **state machine** over the frozen Oracle **GlobalCore** legacy,
but there was **no money control and no accounting**: a reserve or a loss payment moved the legacy's state
with no four-eyes approval and never reached the general ledger. An insurer must (a) control who may commit
reserves and authorise payouts (threat T7 — a settle above authority is a real disbursement), and (b) post
claims to the GL — reserve movements → technical provisions, loss payments → claims-paid/cash. Neither
existed.

## 2. Constraints
- **A frozen legacy.** GlobalCore (Oracle/SOAP) is strangled, never edited — it only knows *applied*
  reserves/settlements, so it cannot hold an in-flight approval.
- **Money integrity + auditability** — integer units, balanced double-entry, who-did-what recorded (DORA/
  Solvency II).
- **Don't rebuild a ledger** — adopt ERPNext (the GL already used for premiums, BILL-014).
- **Module boundaries** — `ktayl-core` enforces Spring-Modulith boundaries (`ApplicationModules.verify()`);
  no reaching into another module's internals/tables.
- **One shared NATS, two environments** — the single-consumer/producer discipline (no dev→prod-GL leak).
- **Segregation of duties** on real cash-out (request ≠ approve ≠ execute).

## 3. Options considered
- **A — workflow in `ktayl-claims`, GL-only seam in `ktayl-core`, one-way async events** *(chosen)*.
- **B — a full claims-finance module (workflow + GL) in `ktayl-core`; claims = state-only.**
- **C — add the workflow + GL inside `ktayl-claims`.**
- **D — fold claims fully into `ktayl-core` now (the ADR-014 end-state).**

## 4. Trade-offs
- **A** keeps the claim whole (state + money together in the service that owns it), and gives `ktayl-core`
  only what it's best at — the ERPNext GL seam it already runs for premiums. Async + one-directional → no
  distributed-monolith call chain. Doesn't start the ADR-014 fold early.
- **B** splits a claim's money from its state across two services — reserve in claims, reserve-*approve* in
  core → chatty cross-service calls per movement; it's the thin end of the fold through the side door.
  **This was my first instinct; the owner's question — *"does that make ktayl-claims obsolete?"* — reframed
  it**: it doesn't, and the GL-only cut is cleaner. Rejected.
- **C** layers greenfield money + four-eyes onto a Strangler whose write model is the external legacy
  (CQRS-lite, no local write store), and duplicates the outbox→ERPNext core already has. Rejected.
- **D** is large and contradicts ADR-014's own *post-legacy-strangler* sequencing. Deferred.

## 5. Decision
**Option A** (→ **ADR-021**). The four-eyes **workflow** (reserve approve, payment request→approve→execute,
large-loss review) lives in `ktayl-claims`; `ktayl-core`'s new `claimsgl` module owns **only** the GL
posting, consuming a published `insurance.claims.financial` event one-way. Not the ADR-014 fold.

## 6. Implementation
- **Emission is CDC-driven, not command-path.** Claims tracks lifecycle *state*, not money — the amounts
  only exist in the `claim_read` CQRS projection, kept current by Debezium CDC from the authoritative legacy.
  So the projector computes the **movement** (new − prior reserve/settlement) and writes an
  `insurance.claims.financial` event to a **transactional outbox atomic with the `claim_read` upsert**; a
  scheduled publisher drains it to NATS JetStream (`CLAIMS_EVENTS`). No command coupling, no second state
  table — `claim_read` *is* the prior state. Deterministic `event_id` → end-to-end idempotency.
- **GL seam (`claimsgl`, ktayl-core).** A durable JetStream consumer → its own transactional outbox →
  `@Scheduled` drainer → balanced ERPNext Journal Entry. Mapping (config, French PCG): reserve increase → DR
  *Claims incurred (6561)* / CR *Outstanding claims provision (4675)*; downward adjustment → the reverse;
  payment → DR provision / CR *Cash (5121)*. Self-contained (WET-first; extract a shared ledger on the 3rd
  GL consumer — modularity forbids reaching into billing's ledger).
- **Four-eyes (ktayl-claims).** Above-authority **or** large-loss reserve → held PENDING in a claims-side
  store → a *different*, sufficiently-authorised person approves → applied to the legacy. Payment goes
  further — **request → approve (four-eyes) → execute (a non-requester executor)** — only *execute* disburses
  (`legacy.settle`); approval alone never pays. Below authority + not large-loss still applies directly.
- **State / secrets.** Pending-approval + the outboxes live in the claims/core Postgres; ERPNext creds via
  ESO→Vault; the claims API uses `X-Claims-*` headers (Authentik OIDC deferred, ADR-005).

## 7. Failure behaviour
- **GL/ERPNext down** → the outbox row stays `pending` → retried; claims/invoicing never blocked; idempotent
  by `(event_type, reference)` / `event_id`, so a CDC redelivery never double-posts.
- **Unbalanced JE** → impossible (balance enforced at `JournalEntry` construction).
- **Prod activation gap (the lesson, §9)** → the producer being dormant until promoted is caught only by
  checking the live image/migrations, not by green CI.

## 8. Measurement
- **Tests:** full suites green — `ModularityTests` + `HexagonalArchitectureTests`; L1 policy/service; WebMvc
  (202/approve/execute/403); **L2 testcontainer ITs** (real Postgres V3/V4/V5 + real NATS for the ingest
  chain). ktayl-claims 77 non-container tests; ktayl-core suite green.
- **Live prod proof (real, not synthetic):** `POST /api/claims/CLM-2026-000023/reserve €30,000` → `200
  RESERVED` via the live SOAP Oracle legacy → ~5 s Debezium CDC → emitter → `CLAIMS_EVENTS` → `claimsgl` →
  **JE `ACC-JV-2026-00010`**, balanced €30,000 (DR 6561 / CR 4675), submitted. Consumer-only dry run earlier:
  JE `ACC-JV-2026-00009`. Cleaned up (JEs cancelled GL-neutral, outboxes → 0).

## 9. Lesson learned
- **"Merged + board-closed" ≠ "live on prod."** I closed the epic after the code merged and the *consumer*
  was proven with a synthetic event — but the claims **producer** image was never Kargo-promoted to prod
  (prod ran a pre-CLM-FIN-02 image; `claim_event_outbox`/V3 absent). Green CI can't see that. The owner's
  "what do you mean by the caveat?" surfaced it. **Verify the live image + applied migrations before
  declaring a cross-service chain done** — each side needs its own promotion (the consumer *and* the
  producer), exactly like the premium chain.
- **Put a concept where its decision lives, and keep the claim whole** — the GL-only cut (vs the tempting
  full-module-in-core) avoided split-brain and respected the frozen legacy. The owner's pushback drove it.
- **Adopt the ledger, model the domain** — ERPNext receives balanced lines; the insurance meaning
  (provisions vs claims-paid) is modelled in the app and expressed as debits/credits.

---
**Proof-catalog row:** a *Backend / money domain* line in the central
`minicloud-gitops/docs/evidence/proof-catalog.md`, cross-linking this case study + ADR-021.
**System-design patterns used:** Transactional Outbox (×2), CQRS read-model, CDC-driven integration,
maker-checker / segregation-of-duties, adopt-don't-build (GL).
**ADR:** [ADR-021](https://github.com/andrelair-platform/ktayl-core/blob/main/docs/architecture/adr/ADR-021-claims-gl-seam.md)
**Publish:** *"Claim money control across a frozen legacy: four-eyes in the Strangler, the ledger in the
monolith, joined by one CDC-driven event."*
