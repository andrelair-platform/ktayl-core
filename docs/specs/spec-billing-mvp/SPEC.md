# SPEC — Billing MVP (epic BILL-01): premium → cash → GL

Compact per-epic implementation contract derived from `docs/prd.md` + `docs/architecture.md`.
Epic owner: TL/BE. Board: ktayl-core (Insurance LOB). Build only after the governance + readiness gate.

## Epic outcome
A bound policy in the live PAS becomes an issued premium invoice, is paid, and both legs post balanced
Journal Entries to ERPNext GL — demonstrated end-to-end on dev, QA'd, promoted to prod.

## Stories

### BILL-010 — Project scaffold + modular-monolith skeleton · infra · P1 · 5
Spring Boot 3.4 + Spring Modulith 1.3, Java 21, Maven; `billing` + `shared` packages; Postgres datasource
+ Flyway (schema `billing`); Authentik OIDC resource-server; `/actuator/health`.
- **AC** ✓ app boots; ✓ **ModularityTests `ApplicationModules.verify()` passes** (the boundary guard exists
  from day one); ✓ Flyway creates the `billing` schema; ✓ `/actuator/health` = UP; ✓ `/api/**` is 401 unauthenticated.
- **AC (fail)** ✗ a deliberately-planted cross-module internal reference makes `verify()` FAIL (prove the guard bites).
- **DoD** L0+L1 green incl. the modularity test; Dockerfile (temurin build → jre runtime, non-root).

### BILL-011 — Ingest bound policy from the PAS (PolicyClient) · integration · P1 · 8
- **AC** ✓ `PolicyClient` fetches a bound policy by ref → {premium minor-units, currency, holder, inception, LOB};
  ✓ poll-reconcile sweep of recently-bound policies (D-INT rec: start here); ✓ idempotent (seen policy → skip); ✓ audited.
- **AC (fail)** ✗ unknown/!bound policy → handled (no invoice, logged), not a 5xx; ✗ PAS down → retried, nothing lost/duplicated.
- **L3 contract test** against the policy-service OpenAPI (wire-format/field pins — the RFC3339 trap).
- **DoD** a bound test policy produces an ingest record; contract test green.

### BILL-012 — Raise premium invoice + installment schedule · domain · P1 · 8
- **AC** ✓ on an ingested bound policy → **one** invoice (`issued`) with installments (MVP: single or N-equal);
  ✓ **invoice total reconciles to Σ installments to the cent** (money = minor units, integer); ✓ idempotent per policy (AC1);
  ✓ domain event `PremiumInvoiced` emitted.
- **AC (fail)** ✗ bind replay does not duplicate the invoice; ✗ rounding reconciles (last installment absorbs the remainder).
- **DoD** L1 money-math + reconciliation + idempotency tests; L2 (real Flyway/JPA) green.

### BILL-013a — Initiate payment via Stripe (SEPA DD PaymentIntent) · integration · P1 · 5
- **AC** ✓ `PaymentGatewayClient` creates a **SEPA-DD PaymentIntent** for an installment (idempotency-key
  = payment `client_key`); ✓ persists `payment(status=pending, psp_intent_id)`; ✓ event `PaymentInitiated`; ✓ audited.
- **AC (fail)** ✗ over-payment / payment on a settled installment → **4xx, not 5xx** (rejected before Stripe);
  ✗ re-initiate with the same client_key → the same intent, no duplicate; ✗ Stripe down → retried, stays `pending`.
- **L3 contract test** vs the Stripe PaymentIntents API (fixtures). **DoD** L1+L2; a test installment yields a pending intent.

### BILL-013b — Capture on the signature-verified Stripe webhook · integration/security · P1 · 5
- **AC** ✓ `POST /webhooks/stripe` verifies the `Stripe-Signature` (webhook signing secret) → on
  `payment_intent.succeeded` marks `payment=succeeded`, installment→`paid`, invoice→`settled` when all paid;
  ✓ **idempotent** by `webhook_event.psp_event_id` (replay = no-op); ✓ emits `PaymentCaptured`; ✓ audited;
  ✓ payment+installment commit in one DB transaction, event marked processed only after commit.
- **AC (fail)** ✗ bad/forged signature → 400, ignored; ✗ webhook before its intent record → parked + reconciled, not dropped;
  ✗ the endpoint is reachable WITHOUT SSO (M2M) but ONLY accepts signature-valid payloads.
- **Test Clocks** simulate due-dates advancing for the installment demo. **DoD** L1+L2; dev loop via `stripe listen`.

### BILL-014 — Post double-entry to ERPNext GL (LedgerClient + outbox) · integration · P1 · 8
- **AC** ✓ on `PremiumInvoiced` → JE `DR Premium Receivable / CR Premium Income`; on `PaymentCaptured` →
  JE `DR Cash/Bank / CR Premium Receivable`; ✓ **every JE balances** (Σdebits = Σcredits); ✓ posted via a
  **transactional outbox** (`ledger_outbox`) + a retry drainer; ✓ a GL blip never blocks billing + never half-posts (AC4).
- **AC (fail)** ✗ ERPNext down → outbox `pending`, billing still works, drains on recovery; ✗ no duplicate JE on retry (idempotent post ref).
- **L3 contract test** vs the ERPNext Journal Entry API; seeded chart-of-accounts mapping.
- **DoD** a payment on dev produces a **balanced JE visible in ERPNext**; L2+L3 green.

### BILL-015 — who-owes-what + audit query · domain/api · P2 · 3
- **AC** ✓ per-policy invoice + installments + outstanding; ✓ append-only audit list; ✓ Finance-group-gated; ✓ actor from identity.
- **DoD** API returns correct outstanding reconciling to GL; authz test (401/403).

### BILL-016 — Deploy: GAP wrapper chart + Kargo git-Warehouse + netpol + ESO + Stripe webhook route · infra · P1 · 8
- **AC** ✓ `services/ktayl-core/helm/` wrapper chart (dev+prod), `releaseName: ktayl-core`; ✓ ns `ktayl-core`/`-prod`,
  quota-fitted (JVM footprint per PRD cost); ✓ **Kargo git-Warehouse** (commit-keyed, block-style YAML tag);
  ✓ default-deny netpol — egress **DNS + PAS + ERPNext + PG + `api.stripe.com:443`** (the governed external
  exception); ✓ ESO→Vault secrets incl. **Stripe test key + webhook signing secret** + the ExternalSecret `ignoreDifferences`;
  ✓ **prod Stripe webhook** = a Cloudflare-tunnel route `billing.devandre.sbs/webhooks/stripe` with forward-auth
  bypassed for that path only (dev uses `stripe listen`).
- **DoD** ArgoCD Synced/Healthy on dev; a live Stripe test webhook reaches the pod + verifies; Kargo promotes
  dev→prod via the CODEOWNERS PR after the QA gate.

## Sequence
BILL-010 → BILL-011 → BILL-012 → BILL-013a → BILL-013b → BILL-014 → (BILL-015 ∥ BILL-016). ≈50 pts —
one focused sprint for the MVP money thread (the Stripe split + webhook route add ~8 pts over the mock-payment version).

## Readiness gate

| Check | Verdict |
|---|---|
| Business need grounded | ✅ Billing is the one missing link bind→cash (verified: no finance ns deployed; PAS+ERPNext live) |
| Stack decided + justified | ✅ ADR-001 (Spring Boot + Spring Modulith) — owner decision 2026-10-05 |
| Architecture + boundary | ✅ `docs/architecture.md`: modular-monolith, 2 contracts (PAS/ERPNext), outbox, netpol, deploy |
| NFR/security/compliance/cost | ✅ PRD sections present (money=minor-units, async GL, PII/egress, IFRS17/DORA, JVM footprint vs quota) |
| Payment PSP decided | ✅ ADR-002: Stripe test mode + SEPA DD, webhook-driven async capture (owner, 2026-10-05) |
| Mock discipline | ✅ all three external boundaries (PAS, ERPNext, **Stripe**) have an L3 contract test in the DoD |
| Cross-service contracts pinned | ⚠️ confirm the PAS bound-policy endpoint + ERPNext JE account mapping + the Stripe **webhook signing secret / SEPA test setup** at kickoff (BILL-011/014/013b) |
| Transport choice | ⚠️ D-INT open: start poll-reconcile, add a bound-event later (recommended, not a blocker) |
| Governance gate | ⚠️ **PENDING** — boundary-crossing (money + PII + cross-service + **external PSP: Stripe egress + non-SSO signature-verified webhook**) → SA+SEC review must sign off before build |

**Verdict: PASS (CONCERNS)** — ready to build the MVP epic once the **governance gate** (SA+SEC, now
including the external-PSP surface) signs off and the ⚠️ contract/secret details are confirmed at
BILL-011/013b/014 kickoff. None are design blockers.
