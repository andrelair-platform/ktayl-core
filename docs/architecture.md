# Architecture — ktayl-core (modular monolith) + the Billing module

- **Owner:** AndreLiar (SA/TL) · **Status:** Draft for governance gate (SA+SEC) · 2026-10-05
- Companion: ADR-001 (stack), `docs/prd.md` (Billing MVP). Org-site map page added at as-built.

## 1. The spine (C4 — Context)

```
                 Authentik (OIDC)
                      │ SSO
 Finance user ──▶ Ingress (internal, Tailscale) ──▶ ktayl-core
                                                       │
         ┌─────────────────────────────────────────────┼───────────────────────────┐
         ▼ consumes                                      ▼ posts                      ▼ owns
 ktayl-policy-service (PAS, live)              ERPNext GL (live, `erp` ns)      PostgreSQL (CNPG/STS)
   bound policy: premium, holder,               Journal Entries                  schema-per-module
   inception, LOB, policyRef                    (double-entry)                   (`billing` schema)
```

ktayl-core is **one deployable**. It does not own policy data (PAS does) and does not own the ledger
(ERPNext does) — it owns the **billing** domain and integrates with the other two as external systems.

## 2. Container / module view (C4 — Container)

```
ktayl-core  (Spring Boot 3.4, one pod set)
  com.ktayl.core
    ├── billing/                         ← Spring Modulith @ApplicationModule
    │     api/         REST controllers (SSO-gated), DTOs
    │     domain/      Invoice, Installment, Payment aggregates; InvoiceService (@Transactional);
    │                  domain events: PremiumInvoiced, PaymentCaptured
    │     persistence/ JPA entities + repositories, Flyway migrations → schema `billing`
    │     ledger/      LedgerPostingListener (on domain event) → LedgerClient (outbox-backed)
    ├── shared/                          ← cross-cutting, NOT a business module
    │     config/      datasource, Flyway-per-schema, security (OIDC resource server)
    │     integration/ PolicyClient (→ PAS), LedgerClient (→ ERPNext)   [the two boundary ports]
    └── KtaylCoreApplication
  test/ ModularityTests  → ApplicationModules.verify()   ← FAILS THE BUILD on a boundary violation
```

**Module discipline (enforced):** `billing` may call another module only via its **public interface**
or an **application event**; never another module's internals or schema. `shared` holds only
cross-cutting infra (no business logic). The verification test is the guardrail — do not weaken it.

## 3. The two integration contracts (both L3 contract-tested — mock discipline)

| Port | Direction | Contract | Failure behaviour |
|---|---|---|---|
| **PolicyClient** | ktayl-core → PAS | GET bound policy by ref → {premium (minor units), currency, holder, inception, LOB}. Pinned to the policy-service OpenAPI; a **contract test** validates the shape (the RFC3339/field-drift trap from underwriting). | PAS down → ingest retries; a missing/!bound policy → 4xx, no invoice. |
| **LedgerClient** | ktayl-core → ERPNext | POST a **Journal Entry** (accounts + debit/credit lines, must balance). Contract test vs the ERPNext API; a seeded chart-of-accounts mapping (Premium Receivable / Premium Income / Cash). | **Async + outbox + retry** — a GL blip never blocks invoicing/payment; the money move is never half-committed. |

**Bind ingestion transport (decide at kickoff, D-INT):** either (a) **NATS** subscribe to a
policy-bound event (consistent with claims CDC / HR lifecycle — preferred, replayable), or (b) a
**poll-reconcile** PolicyClient sweep of recently-bound policies (self-healing, no producer change).
MVP can start with (b) and add (a) — recommendation: start poll-reconcile, add the event when PAS emits one.

## 4. Data model (billing schema)

```
invoice(id, policy_ref, policyholder_ref, currency, total_minor, status[draft|issued|settled|void],
        issued_at, created_at)              -- total_minor reconciles to Σ installments (AC2)
installment(id, invoice_id→, seq, due_date, amount_minor, status[open|paid], paid_at)
payment(id, installment_id→, amount_minor, client_key UNIQUE, captured_at)   -- client_key = idempotency (AC3)
ledger_outbox(id, event_type, payload_json, status[pending|posted|failed], attempts, posted_ref, created_at)
audit_log(id, entity, entity_id, action, actor, at)    -- append-only (DB rewrite rules), as in ktayl-iam
```
Money is **minor units (integer)**, never float. All writes in a module transaction; the GL post is via
`ledger_outbox` (transactional outbox) so billing-state and the external post don't share a 2PC.

## 5. Security / authz / secrets
- Authentik **OIDC resource server**; every `/api/**` authenticated + **Finance-group** authorized; actor
  from the token, never a payload. Console/portal SSO-gated (internal ingress only — CONFIDENTIAL/PII).
- Secrets via **ESO → Vault** (`secret/platform/ktayl-core` dev / `…-prod`): DB creds, PAS client creds,
  ERPNext API key. Never baked (guard-write enforces).
- **Append-only audit** of every invoice/payment/GL-post.

## 6. NetworkPolicy / egress (default-deny)
- Ingress: only ingress-nginx → ktayl-core:8080.
- Egress: **only** DNS + PAS (`ktayl-policy-service` ns) + ERPNext (`erp` ns) + its own Postgres (+ NATS
  if transport (a)). Nothing else — the governed-egress / zero-trust posture.

## 7. Deployment
- **GAP wrapper chart** `minicloud-gitops/services/ktayl-core/helm/` (library `minicloud-app-deployment`),
  single Helm source, `helm.releaseName: ktayl-core`; ns `ktayl-core` / `ktayl-core-prod`.
- Image: multi-stage **temurin build → temurin-jre runtime** (non-root; Gatekeeper non-root + drop NET_RAW).
- **Kargo = git-Warehouse** (NOT image/NewestBuild — the JVM base-layer fixed date breaks ranking;
  ADR-001). Freight keyed on the commit (`commitFrom(...).ID[0:7]` == CI short-SHA); block-style YAML tag.
- ESO `ignoreDifferences` + `RespectIgnoreDifferences` on the app (contains an ExternalSecret).

## 8. Failure modes + rollback
- **PAS down:** ingestion retries / poll-reconcile catches up; no invoice is lost, none duplicated (AC1).
- **ERPNext down:** `ledger_outbox` holds `pending`; a scheduled drainer retries; billing stays usable;
  outstanding-vs-GL reconciles once ERPNext returns. Never a half-posted entry (AC4).
- **Partial payment write:** single DB transaction — either the payment + installment update commit, or
  neither.
- **Bad release:** prod Rollout/canary brake + revert the Kargo-promoted tag (immutable SHA) via a new PR.

## 9. Testing (Tier-A full set — mock discipline)
- **L0** Checkstyle/Spotless + `mvn verify` compile. **L1** JUnit5 domain unit (money math, reconciliation,
  idempotency) + **ModularityTests** boundary verification. **L2** Testcontainers Postgres (real Flyway +
  JPA + outbox). **L3** contract tests vs the **PAS OpenAPI** + the **ERPNext JE API** (every mocked
  boundary backed by a contract test). **L4** smoke on dev. **L5** live QA gate before prod.

## 10. Governance gate (to clear before build — boundary-crossing: money + PII + cross-service)
- **Architecture review (SA/TL):** this document. **Security review (SEC):** authz model + PII/egress +
  secret handling + the money-never-half-committed invariant. Record the decision in ADR/RACI (the gate
  is `bmad-compliance.md`). Then: SPEC → stories → readiness gate PASS → build.
