# ADR-002 — Payment PSP: Stripe (test mode) + SEPA Direct Debit

- **Status:** Accepted (2026-10-05)
- **Owner:** AndreLiar (SA/TL) · **Decided with:** owner, 2026-10-05
- **Context gate:** `cloud-adoption.md` (external SaaS) + `bmad-compliance.md` governance gate
  (new external boundary + inbound webhook = security surface). Builds on ADR-001.

## Context

Billing must capture premium payment against an installment and reconcile it to the GL. The PRD MVP
first modelled this as an internal "recorded payment command" (a fake PSP). That tests nothing real —
no payment authorization, no asynchronous confirmation, no idempotency-under-retry, no webhook
signature handling. We want a **realistic payment simulation** that exercises the real-world shape
without real money.

## Decision

Use **Stripe in TEST mode** as the PSP, with **SEPA Direct Debit (`sepa_debit`)** as the primary
instrument (card as a quick-demo fallback). Payment becomes an **asynchronous, webhook-driven** flow:

```
create PaymentIntent (sepa_debit, idempotency-key = payment client_key)
   → Stripe processes (test)
   → webhook  payment_intent.succeeded  (SIGNATURE-VERIFIED, idempotent)
   → ktayl-core captures the payment → emits PaymentCaptured → outbox → GL post
```

Use **Stripe Test Clocks** to simulate installment due-dates advancing for the schedule demo.

## Why

- **Domain-correct.** EU insurance premium is collected by **SEPA Direct Debit / bank transfer**, not
  credit card. `sepa_debit` keeps the simulation honest. (Card is kept only for a fast visual demo.)
- **Real architecture, not a mock.** Async webhook + signature verification + idempotency keys +
  PSP↔ledger reconciliation is the real money-handling shape — it strengthens the design (and the
  outbox/event pattern already in ADR-001 fits it exactly).
- **Richest tooling + signal.** Stripe test mode gives Test Clocks (installment simulation), the Stripe
  CLI (local webhook forwarding), clean idempotency semantics, and is the strongest portfolio/interview
  signal. (GoCardless sandbox = pure-SEPA, more domain-accurate but smaller tooling; Mollie = EU
  multi-method — both rejected on tooling + signal.)
- **DORA closed loop.** Stripe is an **external third-party ICT provider** → a concrete entry for the
  ktayl-IS **DORA third-party register** and a live tie-in to Retrieva's thesis.
- **cloud-adoption gate — PASS:** real need (realistic payment capture, the money chain's weakest MVP
  point) · justified (DORA third-party + demonstrable premium collection) · **free** (test mode, no
  charges, no PCI scope, no real money) · layer = ktayl-IS · **destroyable** (delete the test keys;
  no standing cost).

## Consequences

- **New boundary port `PaymentGatewayClient`** → Stripe API (`api.stripe.com:443`). This adds an
  **external-internet egress** allow to the otherwise default-deny posture (previously only PAS +
  ERPNext + Postgres) — a governed-egress exception the SEC gate must approve.
- **New inbound webhook endpoint** `POST /webhooks/stripe` — **NOT SSO-gated** (M2M from Stripe),
  secured by **Stripe signature verification** (`Stripe-Signature` + the webhook signing secret) +
  **idempotent** handling (replayed events are no-ops). Reachability:
  - **dev:** `stripe listen --forward-to` (Stripe CLI) — no public exposure needed.
  - **prod:** a Cloudflare-tunnel route `billing.devandre.sbs/webhooks/stripe` (that one path bypasses
    the Authentik forward-auth; the rest of the app stays SSO-gated).
- **Secrets (ESO→Vault `secret/platform/ktayl-core*`):** `stripe-secret-key` (test), `stripe-webhook-secret`.
  Never baked. Test keys only — a leak has no financial impact, but still treated as a secret.
- **Payment is now eventually-consistent:** the API that initiates payment returns `pending`; the
  installment flips to `paid` only on the verified webhook. The UI/who-owes-what reflects `pending`.
- **Honest domain caveat (state it):** real *corporate/B2B* premium often settles via **broker
  account-current**, not direct SEPA DD. This simulates the **direct retail/SME collection channel**;
  broker settlement is a later **Distribution** concern, not Billing MVP.

## Follow-ups
- BILL-013 splits into **payment-intent create** (idempotent) + **webhook capture** (signature-verified).
- A new wiring story: Stripe test keys → Vault/ESO, the webhook route (dev CLI / prod tunnel), signature
  verification + idempotent webhook handler.
- L3 contract test against the Stripe PaymentIntents + webhook event shape (test fixtures).
