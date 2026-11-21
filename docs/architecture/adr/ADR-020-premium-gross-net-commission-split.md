# ADR-020 — Premium gross/net/commission split: a billing concern, posted as a 3-leg GL entry

**Status:** Accepted (2026-11-21) · **Owner:** SA/TL · **Context:** BILL-012b extends the Billing `invoice`
so a premium is modelled as **gross = net + commission** — the one insurance-billing concept a generic GL
(ERPNext) cannot express on its own. This ADR records *where* the split is computed, *why* it is a billing
(not underwriting) concern, and *how* it reaches the ledger without breaking the money invariants already in
place (BILL-012/013/014).

## Context — the invoice was gross-only
Billing ingested the Underwriting `bound-risk` event (ADR-003), raised a single-figure `invoice.total_minor`
(= Σ installments), and posted a **2-leg** Journal Entry `DR Premium Receivable / CR Premium Income`
(BILL-014). That treats the whole premium as the insurer's income. In reality an intermediated premium is
**gross** (what the policyholder owes) = **net** (what the insurer earns) + **commission** (the broker's
remuneration). Without the split, the GL overstates income and hides the commission liability — and ERPNext,
a generic double-entry ledger, has no notion of "insurance commission"; the split must be computed upstream
and handed to it as plain debit/credit lines.

## Decision
Compute the split **in Billing**, persist it on the invoice, and post a **3-leg** premium JE. The
policyholder is still billed and collected on **gross**, so nothing downstream of invoicing changes.

1. **Commission is a billing/distribution concern, applied in Billing — zero Underwriting change.** The
   `bound-risk` event carries only the gross `premium_minor` (ADR-003); a commission *schedule* is a
   distribution decision, not a risk/pricing one. Billing resolves the rate (`CommissionSchedule`) and splits
   the gross (`CommissionSplit`). Underwriting, the event contract, and its L3 contract test are untouched.
2. **Rate = basis points, config-driven, per `product_code` with a default.** `billing.commission.default-bps`
   (1500 = 15%) + a `rates.<PRODUCT_CODE>` override map. Integer eurocents throughout; commission rounds
   **half-up** and the **net absorbs the complement**, so `net + commission == gross` to the cent — a lost or
   invented cent is impossible. Enforced in `CommissionSplit`'s constructor **and** by a DB CHECK
   (`net_minor + commission_minor = gross_minor`), plus `gross_minor = total_minor` so the gross stays the
   billed figure.
3. **`gross == total_minor` → the schedule, payment and dunning are unchanged.** `InstallmentScheduler`
   still splits the **gross**; BILL-013 capture and BILL-013c dunning operate on gross installments. The
   split is a GL/reporting concern only — it does not touch who-owes-what or how-much-is-collected.
4. **The GL `PREMIUM_INVOICED` entry becomes 3-leg**: `DR Premium Receivable (gross) / CR Premium Income
   (net) / CR Commission Payable (commission)`. The commission leg is **omitted when commission is 0**,
   collapsing to the classic 2-leg — so a zero-commission product (or pre-split invoice) posts exactly as
   before. Balance (Σdr = Σcr) is still enforced at `JournalEntry` construction.
5. **The outbox `amount_minor` stays GROSS; commission rides a new `commission_minor` column.** The
   transactional-outbox contract (BILL-014, ADR-not-needed — it's the V8 mechanism) is preserved: the
   `finance` reconciliation + premium register (FINCLOSE-01) key the *premium invoiced* figure off the outbox
   amount, which must remain **gross**. The commission is carried alongside so the drainer can build the 3rd
   leg, without changing what "premium invoiced" means to the finance module.
6. **The invoice API is Finance-gated.** Raising/reading an invoice now exposes the broker commission
   (commercially sensitive), so `InvoiceController` is `@PreAuthorize("hasAuthority('GROUP_Finance')")` — the
   same gate as `PaymentController`. Auto-invoicing runs through the in-process `InvoiceOnIngestListener`, not
   the HTTP endpoint, so the bound-risk → invoice chain is unaffected by the gate.

### Options considered
- **A — split in Billing, config-driven bps, 3-leg GL** *(chosen)*. The split lives where the commercial
  relationship lives (billing/distribution); Underwriting stays a pure risk/pricing concern; the GL learns
  the split as ordinary debit/credit lines. Minimal surface, honours ADR-003 (no UW change) and the money
  invariants.
- **B — carry net + commission on the `bound-risk` event (compute in Underwriting).** *Rejected:* commission
  is not a risk/pricing output; baking it into the UW contract couples a distribution decision to underwriting,
  forces a UW change + an L3 contract-test change for a concern UW doesn't own, and would need re-plumbing
  every time a commission schedule changes. Keep the event about the risk.
- **C — don't split; book gross as income and handle commission as a separate expense later.** *Rejected:*
  overstates premium income at invoice time, defers the liability, and loses the net figure the insurer
  actually earns — the exact distortion this ADR removes. A generic GL won't derive it for us.
- **D — model commission as a Stripe/payment-time deduction.** *Rejected:* commission is owed on **binding**,
  independent of whether/when the policyholder pays; tying it to capture would misstate the liability for
  unpaid or dunned policies.

## Consequences
- **Good:** income is stated net, the commission liability is explicit on `4086 Commissions de courtage à
  payer`, the split reconciles to the cent (DB-enforced), and rates are tunable per LOB by config with **no
  code change**. Backward-compatible: zero-commission → 2-leg, identical to pre-split; existing invoices were
  backfilled (`gross=total, commission=0, net=gross`).
- **Cost / watch-outs:** a `Map` config (`commission.rates`) can't bind from underscore env vars → prod
  injects it via `SPRING_APPLICATION_JSON`. A new liability account must exist in ERPNext before the 3-leg JE
  posts (the drainer retries, so a missing account never blocks invoicing — it just stays pending). The split
  is flat-per-product today; tiered/volume commission, clawbacks, and per-broker rates are **non-goals**
  (revisit when a real distribution module needs them).
- **Enforced by:** `CommissionScheduleTest` (rounding/reconciliation/overrides) · `JournalEntryFactoryTest`
  (3-leg balanced, 2-leg when zero) · the invoice integration test (split persists + satisfies the DB CHECKs)
  · the live QA gate (`tests/qa/billing-commission-qa-gate.sh`, dev 22/22) · the prod 3-leg JE proof.

## Live proof (prod, 2026-11-21)
A synthetic `COMM_PROP` bound-risk (€4800 gross) → invoice split `gross 480000 / net 408000 / commission
72000 @ 1500 bps` → the drainer posted **Journal Entry `ACC-JV-2026-00007`**, submitted + balanced €4800:
`DR 4111 Clients 4800.00 / CR 706 Prestations 4080.00 (net) / CR 4086 Commissions de courtage à payer 720.00
(commission)`. Test rows cleaned; billing back to 0 baseline. See the case study
`minicloud-gitops/docs/evidence/case-studies/bill-012b-premium-commission-split.md`.
