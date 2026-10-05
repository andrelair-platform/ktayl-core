# PRD — ktayl-core Billing (MVP thin thread): premium → cash → GL

- **Product:** ktayl-core (insurance-LOB modular monolith) · **Module:** billing · **Path:** C (new product)
- **Owner (PM/PO):** AndreLiar · **Status:** Draft for governance + readiness gate · 2026-10-05
- **Initiative:** Insurance LOB · **Theme:** close the Tier-1 operating spine (the money chain)

## Problem / driver

The insurance operating spine runs **Submission → Underwriting (live) → Policy bind (live) → Billing →
Claims (live)**. Today a policy can be **bound** but there is **no mechanism to turn it into cash**: no
premium invoice, no payment capture, no posting to the general ledger. Billing is the single missing
link between a bound policy and money. This PRD covers the **thinnest end-to-end thread** that closes it.

## Goal (MVP scope)

> *As the insurer, when a policy is bound in the PAS, I want the system to raise the premium invoice,
> capture payment, and post both legs to the general ledger, so that a bound policy becomes recognised,
> collectable, and accounted-for revenue.*

**In scope (MVP):**
1. **Ingest a bound policy** from the live PAS (`ktayl-policy-service`) — premium, policyholder,
   inception date, LOB, policy ref.
2. **Raise a premium invoice** with an **installment schedule** (MVP = single installment or simple
   N-equal installments; annual premium).
3. **Capture a payment** against an installment via **Stripe (test mode), SEPA Direct Debit** (ADR-002):
   create a PaymentIntent → Stripe processes → a **signature-verified webhook** confirms → capture.
   Payment is **asynchronous** (initiate returns `pending`; the installment flips to `paid` on the
   verified webhook). Card is a quick-demo fallback; **Stripe Test Clocks** simulate installment due-dates.
4. **Post double-entry to ERPNext GL** (adopt): on invoice `DR Premium Receivable / CR Premium Income`;
   on confirmed payment `DR Cash/Bank / CR Premium Receivable`.
5. **Expose state** — invoice + installments + paid/outstanding/**pending** (who-owes-what), per policy.

**Out of scope (later slices):** dunning/reminders, commission netting to Distribution, IFRS 17
measurement, reinsurance cession billing, credit notes/mid-term adjustments, multi-currency, **broker
account-current settlement** (the real B2B premium channel — a later Distribution concern; MVP simulates
the direct SEPA-DD collection channel). Claims outflow is the already-live Claims service, exercised
against the same policy later.

## Users / actors
- **PAS (system)** — source of the bound-policy event/record (upstream).
- **Billing clerk / Finance (human)** — views invoices, confirms payments (SSO via Authentik, Finance group).
- **ERPNext GL (system)** — the ledger of record (downstream).

## Acceptance criteria (product-level)
- **AC1** A bound policy in the PAS results in exactly **one** premium invoice (idempotent — replaying the
  bind does not duplicate).
- **AC2** Invoice total **reconciles to the sum of its installments** to the cent (money = minor units).
- **AC3** A payment reduces outstanding by exactly its amount; over-payment / payment on a settled
  installment is rejected (4xx, not 5xx); payments are idempotent by a client key. The Stripe **webhook
  is signature-verified** (reject a bad/forged signature) and **idempotent** (a replayed event is a no-op).
- **AC4** Each invoice-issued and payment-captured event posts a **balanced** Journal Entry to ERPNext
  (debits = credits); a GL-post failure leaves the billing state consistent + retryable (never a
  half-committed money move).
- **AC5** who-owes-what returns correct outstanding per policy; audit trail of every state change.
- **AC6** Every API is **authenticated** (Authentik) and authorized to the Finance group; the actor is
  taken from the identity, never a payload field.

## Non-functional requirements (NFR)
- **Availability:** dev 1 replica / prod 2; the GL post is **asynchronous + retried** so an ERPNext blip
  never blocks invoicing. **RTO/RPO** = platform standard (CNPG backup; this is a prod DB → backup gate).
- **Latency:** invoice/payment API p95 < 300 ms (the GL post is off the request path).
- **Scale:** portfolio-grade for a simulated insurer (hundreds of policies) — not a throughput concern.
- **Consistency:** money operations are **transactional** (`@Transactional`); the ledger post is an
  **outbox/event** so billing-state and GL-post don't share a distributed transaction.
- **Observability:** structured logs, `/actuator/health` + Prometheus metrics; a counter for GL-post
  success/failure + outstanding-premium gauge.

## Security requirements
- **AuthN/AuthZ:** Authentik OIDC resource-server; Finance-group-gated; no app-local users.
- **Data classes:** policyholder identity + premium amounts = **CONFIDENTIAL/PII** → server-side only,
  default-deny egress, no public surface (internal ingress, Tailscale).
- **Secrets:** DB creds + PAS/ERPNext credentials + **Stripe test secret-key + webhook-signing-secret**
  via **ESO → Vault** (`secret/platform/ktayl-core*`), never baked. **Trust boundary:** ktayl-core
  reaches PAS + ERPNext + its own Postgres + **the Stripe API (external egress, governed allow)**; the
  only inbound non-SSO surface is the signature-verified **`/webhooks/stripe`** endpoint (M2M from Stripe).
- **Audit:** append-only audit of invoice/payment/GL-post + webhook events (money + auditability are hard
  requirements).
- **Third-party ICT (DORA):** Stripe is an external provider → recorded in the ktayl-IS DORA third-party
  register. Test mode only — **no real money, no PCI scope** (ktayl-core never touches card/IBAN data;
  Stripe tokenises).

## Compliance mapping
- **Solvency II** — premium recognition feeds technical provisions/own-funds (the IS context).
- **IFRS 17** — premium is a measurement input; MVP records the receivable/income cleanly so a later
  slice can layer measurement (explicitly deferred, not ignored).
- **GDPR** — policyholder PII minimised + audited; CONFIDENTIAL handling.
- **DORA** — the billing↔PAS↔GL↔**Stripe** integration is ICT inter-dependency; **Stripe is a
  third-party ICT provider** (register entry); resilience = retried async GL post + idempotent
  signature-verified webhooks + backup.
- **ACPR/EIOPA** — premium accounting is regulated reporting input (via ERPNext GL).
- Certification evidence: **BC02 (concevoir/développer)** + **BC03 (déployer & sécuriser)**.

## Cost / capacity (stated before code — tight 5-node cluster)
- **Cluster footprint:** a JVM pod is heavier than Node — budget **~512Mi–768Mi request / 1Gi limit** per
  replica (dev 1, prod 2) + Postgres (shared, schema-per-module; ~256Mi/5Gi PVC). Must fit the insurance
  namespace ResourceQuota (`manifests/quotas/`) — confirm headroom before prod.
- **LLM/token cost:** none (no AI in Billing MVP).
- **Cloud cost:** none — **Stripe test mode is free** (no charges, no standing resource; destroyable =
  delete the test keys). Passes the `cloud-adoption.md` gate (real need · DORA justification · free · layer=ktayl-IS · one-command teardown).
- **Storage growth:** one Postgres schema + append-only audit — negligible at portfolio scale.

## KPIs / success metrics
- A bound test policy → invoice → payment → **two balanced Journal Entries visible in ERPNext** (the
  end-to-end money link demonstrated live).
- 0 unbalanced GL posts; 0 duplicate invoices under bind replay; outstanding-premium reconciles to GL.
