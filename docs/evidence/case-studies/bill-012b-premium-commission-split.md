# Case Study — Premium gross/net/commission split (ktayl-core Billing, BILL-012b)

> Modelling insurance commission inside a billing module on top of a generic double-entry GL (ERPNext),
> proven end-to-end in production as a balanced 3-leg Journal Entry. Decision of record: ADR-020.

## 1. Business problem
`ktayl-core` Billing turned a bound policy into cash and posted it to the ledger, but it treated the premium
as a **single figure** — the whole amount booked as insurer income. An intermediated insurance premium is
**gross = net + commission**: gross is what the policyholder owes, net is what the insurer actually earns, and
commission is the broker's remuneration (a liability the insurer owes the intermediary). Without the split the
GL **overstated premium income** and **hid the commission liability** — and ERPNext, a generic double-entry
ledger, has no "insurance commission" concept, so nothing downstream would derive it. The split had to be
computed in the application and handed to the GL as plain debit/credit lines.

## 2. Constraints
- **Money integrity** — eurocents integers only (never floats); the split must reconcile to the cent
  (`net + commission == gross`), DB-enforced, not just app-enforced.
- **Don't disturb the collected amount** — the policyholder is still billed on gross; installments, Stripe
  capture (BILL-013) and dunning (BILL-013c) must be untouched.
- **Honour the module boundary (ADR-003/006)** — commission is a distribution concern; the Underwriting
  `bound-risk` event and its L3 contract must not change for it.
- **Preserve the finance reconciliation contract (FINCLOSE-01)** — the premium register keys "premium
  invoiced" off the GL outbox amount, which must stay **gross**.
- **Auditability / least privilege** — invoice data now includes commercially sensitive commission → it must
  be access-controlled.
- **Regulation** — Solvency II / IFRS 17 premium recognition wants income stated net of acquisition cost; the
  commission payable is a real liability.

## 3. Options considered
- **A — split in Billing, config-driven basis points, post a 3-leg GL entry.**
- **B — carry net + commission on the `bound-risk` event (compute it in Underwriting).**
- **C — don't split; book gross as income, treat commission as a separate expense later.**
- **D — model commission as a payment-time (Stripe) deduction.**

## 4. Trade-offs
- **A** puts the split where the commercial relationship lives (billing/distribution), keeps Underwriting a
  pure risk/pricing concern, and teaches the GL the split as ordinary lines. Minimal surface; backward
  compatible (commission 0 → the old 2-leg entry). **Cost:** a new liability account must exist in ERPNext;
  a `Map` rate config needs JSON injection (env vars can't express a map).
- **B** couples a distribution decision to the UW contract — a UW code + L3-contract change for data UW
  doesn't own, re-plumbed every time a commission schedule changes. Rejected.
- **C** is the status quo's distortion: overstated income, deferred/invisible liability, lost net figure — a
  generic GL won't reconstruct it. Rejected.
- **D** misstates the liability: commission is owed on **binding**, independent of whether/when the
  policyholder pays (and of dunning). Rejected.

## 5. Decision
**Option A.** Compute the split in Billing (`CommissionSchedule` → `CommissionSplit`), persist gross/net/
commission/bps on the invoice, keep **gross == total_minor** (so collection is unchanged), and post a 3-leg
`DR Premium Receivable (gross) / CR Premium Income (net) / CR Commission Payable (commission)` Journal Entry
(the commission leg omitted when zero). → **ADR-020** (ktayl-core repo).

## 6. Implementation
- **Domain (pure, unit-tested):** `CommissionSplit` (reconciles `net+commission==gross` at construction) +
  `CommissionSchedule` (`@ConfigurationProperties("billing.commission")`, rate in basis points per
  `product_code` + a default; half-up integer rounding via `Math.multiplyExact`, net absorbs the complement).
- **Persistence:** migration **V16** adds `gross_minor / net_minor / commission_minor / commission_bps` to
  `invoice` with CHECK constraints (`net+commission=gross`, `gross=total`, non-negative) and `commission_minor`
  to `ledger_outbox`; existing invoices backfilled (`gross=total, commission=0, net=gross`).
- **Flow:** `InvoiceService.raiseFor` computes the split, persists it, audits it, emits an enriched
  `PremiumInvoiced(gross, net, commission)`. `GlPostingListener` writes an outbox row with **amount = gross**
  + `commission_minor`. `JournalEntryFactory` builds the 3-leg (or 2-leg when commission 0); balance enforced
  at `JournalEntry` construction. `LedgerOutboxDrainer` → `ErpNextLedgerClient` posts/ submits the JE.
- **Boundaries / state:** all state in Postgres schema `billing`; the GL is external (ERPNext, adopted); the
  commission account is config (`LEDGER_ACC_COMMISSION_PAYABLE`), the rates are config
  (`SPRING_APPLICATION_JSON` in prod, since a map can't bind from env vars). No secret handling added.
- **Access control (a QA-gate finding):** `InvoiceController` was only `authenticated()` while the sibling
  `PaymentController` was `GROUP_Finance` — the live QA gate flagged it; gated the invoice API to
  `GROUP_Finance` (auto-invoicing uses the in-process listener, so the chain is unaffected).
- **ERPNext account:** created `4086 - Commissions de courtage à payer - KS` (French PCG), a Liability leaf
  under 408 (Fournisseurs - Factures non parvenues) = accrued broker commission; **no `account_type`** so the
  leg posts without a party (unlike the 4111 receivable, which requires one).

## 7. Failure behaviour
- **Missing/unconfigured commission account or GL down** → the transactional outbox row stays `pending` and
  the drainer retries; **billing is never blocked** (invoicing + collection proceed). Idempotent by
  `(event_type, reference)` → no double-post on replay.
- **Rounding** → reconciliation is structurally guaranteed (net absorbs the complement) and DB-CHECK-enforced;
  a non-reconciling split cannot be inserted.
- **Zero commission** → 2-leg entry, byte-identical to pre-split behaviour (safe rollback shape).
- **A dormant-on-dev config first-exercised in prod** (the recurring BILL risk: dev keeps the consumer off) →
  mitigated by proving the whole chain live in prod before declaring done (below), not by trusting green dev CI.

## 8. Measurement
- **Tests:** full suite green incl. `CommissionScheduleTest`, 3-leg `JournalEntryFactoryTest`, the invoice
  integration test asserting the persisted split against the real DB CHECKs; `ModularityTests` +
  `HexagonalArchitectureTests` pass.
- **Dev QA gate:** `tests/qa/billing-commission-qa-gate.sh` — **22/22 PASS** (authz 401/403/404, API view
  exposes the split, DB reconciles, installments bill gross, outbox carries commission, idempotency).
- **Prod proof:** €4800 gross `COMM_PROP` bound-risk → invoice `gross 480000 / net 408000 / commission 72000
  @ 1500 bps` → **JE `ACC-JV-2026-00007`**, submitted + balanced €4800:
  `DR 4111 4800.00 / CR 706 4080.00 (net) / CR 4086 720.00 (commission)`. Cleaned up (JE cancelled GL-neutral,
  billing back to 0 baseline).
- **Rates in effect (prod):** default 15% · COMM_PROP 15% · MARINE 10% · ENGINEERING 12.5%.

## 9. Lesson learned
- **Put a concept where its decision lives.** Commission is a distribution decision, so it belongs in Billing,
  not in the risk event — which kept the UW contract and its L3 test untouched. Resisting option B is the
  reusable instinct: don't widen an upstream contract for a downstream concern.
- **A generic GL is a ledger, not a domain model.** ERPNext can only receive balanced lines; the *insurance*
  meaning (gross/net/commission) must be modelled in the app and expressed as debits/credits — the clean seam
  that let us adopt the GL instead of building one.
- **Keep the invariant the collector depends on.** Holding `gross == total_minor` and keeping the outbox
  amount gross meant payment, dunning, and the finance premium register needed **no** change — the split was
  additive.
- **Two incidental but reusable gotchas:** a Spring `Map` config can't bind from underscore env vars (use
  `SPRING_APPLICATION_JSON`); prod CNPG writes must target the `-rw` service (the local pod can be a read-only
  standby). Both are now in the project memory.

---
**Proof-catalog row:** the central *Backend / money domain* row in
`minicloud-gitops/docs/evidence/proof-catalog.md` (the one cross-product portfolio hub) now links this case
study + ADR-020 cross-repo and marks the money chain ✅ (3-leg JE `ACC-JV-2026-00007`).
**System-design pattern(s) used:** Transactional Outbox (reused, BILL-014); config-driven domain rule;
adopt-don't-build (GL = ERPNext). Not novel enough to add a new pattern row.
**ADR:** [ADR-020](https://github.com/andrelair-platform/ktayl-core/blob/main/docs/architecture/adr/ADR-020-premium-gross-net-commission-split.md)
**Publish:** a problem-framed post — *"Modelling insurance commission on top of a generic GL: one billing
module, a 3-leg journal entry, reconciled to the cent."*
