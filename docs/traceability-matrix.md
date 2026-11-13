# Traceability Matrix — ktayl-core (Billing + Complaints)

> Threads every requirement **PRD → architecture component → test → pipeline → evidence**, so a change to
> one requirement shows exactly what else must move. Path-C Definition-of-Done item
> (`documentation-standard.md`); template: `minicloud-gitops/docs/templates/traceability-matrix.md`.
>
> **ID provenance (honest):** the PRD currently expresses requirements as **AC1–AC6** + prose **NFR** /
> **Security** sections. The `FR/NFR/SEC` IDs below are introduced here mapping 1:1 to those PRD items —
> **fold them back into `prd.md` on its next edit** so the PRD is the ID source of record.

## Requirement register (ID → PRD item → observable criterion)

| ID | ⟺ PRD | Requirement (observable) |
|---|---|---|
| FR-001 | AC1 | A bound policy yields **exactly one** premium invoice; replaying the bind does not duplicate (idempotent). |
| FR-002 | AC2 | Invoice total **reconciles to the sum of installments** to the cent (minor units). |
| FR-003 | AC3 | A payment reduces outstanding by its amount; over-/settled-installment payment → **4xx not 5xx**; payments idempotent by client key. |
| FR-004 | AC4 | Each invoice/payment posts a **balanced** Journal Entry to ERPNext; a GL-post failure leaves billing consistent + retryable. |
| FR-005 | AC5 | who-owes-what returns correct outstanding per policy + an audit trail of every state change. |
| SEC-001 | AC6 | Every API is **authenticated** (Authentik) + authorized to the Finance group; actor taken from identity, never a payload field. |
| SEC-002 | AC3/sec | The Stripe webhook is **signature-verified** (forged signature rejected) + **idempotent** (replay = no-op). |
| SEC-003 | §arch | `billing` is a **CLOSED** module — no other module reads its tables (data isolation enforced at build). |
| SEC-004 | §1 ingest | The consumed UW **bound-risk event** matches the agreed contract (schema/wire-format). |
| NFR-001 | §NFR latency | invoice/payment API **p95 < 300 ms** (GL post off the request path). |
| NFR-002 | §NFR consistency | money ops are `@Transactional`; the GL post is an **outbox/event** (no distributed txn). |
| NFR-003 | §NFR / data | the `billing` schema matches its migrations (**ERD never drifts**). |
| NFR-004 | §NFR obs | `/actuator/health` + Prometheus metrics (GL-post success/failure counter, outstanding gauge). |

## The matrix

| ID | Architecture component (module / class) | Test (file · case) | Pipeline stage | Evidence / status |
|---|---|---|---|---|
| FR-001 | `billing.ingest` `PolicyIngestService` (idempotent upsert) + `billing.invoicing` `InvoiceService` | `billing/ingest/PolicyIngestServiceTest` · `billing/invoicing/InvoiceServiceTest` | `build-test` (unit) | ✅ tested |
| FR-002 | `billing.invoicing` `InstallmentScheduler` + `InvoiceService` | `billing/invoicing/InstallmentSchedulerTest` · `billing/invoicing/InvoiceIntegrationTest` | `build-test` (unit + integration) | ✅ tested |
| FR-003 | `billing.api` `/api/billing/**` + `/webhooks/stripe`; `shared` Stripe `PaymentGatewayClient` | ⚠️ **none yet** (BILL-013a/b built; payment/webhook test missing) | (Integration + Security) | 🔴 **untested — add `PaymentWebhookIntegrationTest`** |
| FR-004 | `shared` `LedgerClient` + `billing` `ledger_outbox` (async, retried) | ⚠️ **none yet** (BILL-014) | (Integration) | 🔴 **untested — add `LedgerOutboxIntegrationTest`** |
| FR-005 | `billing.api` who-owes-what + audit query | ⚠️ none yet (BILL-015, P2) | (Integration) | 🟠 pending (P2) |
| SEC-001 | `shared` Authentik OIDC resource-server + `billing.api` Finance-group gate | ⚠️ no automated authz test → **live QA gate** covers it | `qa-gate` (live L5) | 🟠 covered live, not in CI — add an authz test |
| SEC-002 | `billing.api` `/webhooks/stripe` signature verify + idempotency | ⚠️ **none yet** | (Security) | 🔴 **untested — highest-value gap (money + external surface)** |
| SEC-003 | `billing` module (CLOSED) boundary | `ModularityTests` (`ApplicationModules.verify`) | `build-test` | ✅ tested (build fails on a cross-module tap) |
| SEC-004 | `billing.ingest` `UnderwritingEventConsumer` ⟵ UW bound-risk event | `billing/ingest/BoundRiskEventContractTest` | `build-test` (contract) | ✅ tested |
| NFR-001 | `billing.api` + outbox (GL off path) | ⚠️ no perf test | (Performance / k6) | 🟠 unmeasured — k6 before a major release |
| NFR-002 | `billing.invoicing` `@Transactional` + `ledger_outbox` | `billing/invoicing/InvoiceIntegrationTest` (partial) | `build-test` (integration) | 🟡 partial — outbox retry path untested (see FR-004) |
| NFR-003 | `data-model/` + Flyway `billing` migrations | `schema-erd-drift` (regenerate ERD from migrations, fail on drift) | `schema-erd-drift` | ✅ CI-gated |
| NFR-004 | `shared` actuator + Prometheus metrics | ⚠️ no metrics assertion | (Smoke / QA) | 🟠 verify live |

## What this matrix reveals (the point of doing it)

Threading the requirements exposes **4 untested requirements that green CI hides today** — a backlog, in
priority order:
1. 🔴 **SEC-002** — the Stripe webhook signature-verify + idempotency has no test, yet it's an **external,
   money-moving inbound surface**. Highest value: `PaymentWebhookIntegrationTest` (forged sig → reject;
   replay → no-op).
2. 🔴 **FR-003** — payment capture (over-/settled-payment → 4xx, idempotency) is untested in CI.
3. 🔴 **FR-004** — the GL outbox (balanced JE, retry on ERPNext blip, no half-commit) is untested.
4. 🟠 **SEC-001 / NFR-001 / NFR-004** — authz, latency and metrics are only checked live (QA gate), not in CI.

> *If SEC-002 changes tomorrow:* edit `billing.api` `/webhooks/stripe` + the `shared` Stripe client →
> update (create) `PaymentWebhookIntegrationTest` → it re-gates at the Security/Integration stage → the
> evidence is that CI run. One row, the whole blast radius.

---

## Complaints module (CMPL-01 slice)

> The Phase-3 hexagonal slice (`com.ktayl.core.complaints`). IDs `CFR/CSEC/CNFR` map to the
> `spec-complaints/SPEC.md` ACs. As-built: [`docs/complaints/as-built.md`](complaints/as-built.md).

| ID | Requirement (observable) | Architecture component | Test (file · case) | Pipeline | Evidence / status |
|---|---|---|---|---|---|
| CFR-001 | Register yields one complaint; same `Idempotency-Key` → same complaint, no dup | `complaints.domain` `Complaint.register` + `application.RegisterComplaint` | `complaints/domain/ComplaintTest` · `application/RegisterComplaintTest` · `…/ComplaintPersistenceAdapterTest` | `build-test` (unit+integration) | ✅ tested |
| CFR-002 | Forward-only lifecycle; wrong predecessor / on CLOSED → 409 | `complaints.domain` `Complaint` state machine | `complaints/domain/ComplaintLifecycleTest` | `build-test` (unit) | ✅ tested |
| CFR-003 | `acknowledge` sets the ack-due **placeholder** clock; overdue is strict-after | `complaints.domain` `acknowledge` / `acknowledgementOverdueAt` | `complaints/domain/ComplaintLifecycleTest` (boundary) | `build-test` (unit) | ✅ tested |
| CFR-004 | **Every** transition writes an append-only audit row (from→to, actor) | `application.ComplaintLifecycle` + `infrastructure.persistence` `AuditTrailAdapter` | `complaints/application/ComplaintLifecycleTest` | `build-test` (unit) | ✅ tested |
| CFR-005 | `GET` returns `ComplaintSummary` (never the entity) | `api.ComplaintSummary` + `application.ComplaintService` | `complaints/infrastructure/web/ComplaintApiContractTest` | `build-test` (L3) | ✅ tested |
| CSEC-001 | Every `/api/**` requires a valid token → 401 | `shared` OIDC resource-server | `ComplaintApiContractTest` (no-token) | `build-test` (L3) | ✅ tested |
| CSEC-002 | Authz = `Complaints Handler` group → 403 without it | `shared` groups→authority + controller `@PreAuthorize` | `ComplaintApiContractTest` (missing-group) | `build-test` (L3) | ✅ tested |
| CSEC-003 | `complaints` is a CLOSED module; only `api` is published | module boundary + `api` NamedInterface | `ModularityTests` | `build-test` | ✅ tested (build fails on a cross-module tap) |
| CSEC-004 | Actor taken from the token, never a placeholder (attribution) | `infrastructure.web` `actor(jwt)` → use cases → audit | `RegisterComplaintTest` (attribution) ; **end-to-end live** | `build-test` + `qa-gate` | 🟡 unit-proven; live attribution at CMPL-014 |
| CNFR-001 | `complaints` schema matches its migrations (ERD never drifts) | `data-model/` + Flyway V4–V6 | `schema-erd` (regenerate + CI auto-commits) | `schema-erd` | ✅ CI-gated |
| CNFR-002 | Domain is framework-pure (survives the tech, ADR-013) | `complaints.domain` | `HexagonalArchitectureTests` | `build-test` | ✅ tested (build fails if domain imports Spring/JPA) |
| CNFR-003 | HTTP contract matches `complaints.openapi.yaml` (404/409/422/401/403/400) | `infrastructure.web` + `ComplaintExceptionHandler` | `ComplaintApiContractTest` | `build-test` (L3) | ✅ tested |
| CNFR-004 | Observability triad live (logs→Loki, trace→Tempo, metrics scrape) — ADR-012 | `shared` actuator + OTel wiring | ⚠️ live only | `qa-gate` (L5) | 🟠 **pending CMPL-014** (first deploy) |

**What's honestly not yet covered (→ CMPL-014, the first ktayl-core deploy + live QA gate):** CNFR-004
(the triad live), CSEC-004 end-to-end attribution over the real token, and the **L4 smoke**
(register→ack→close over the dev ingress). Green CI proves the slice in isolation; the live QA gate is the
backstop before any prod promotion.

---

Keep this matrix current in the **same PR** as any requirement/code change (`documentation-standard.md`).
