# ktayl SI modernization roadmap — consolidate the insurance core into `ktayl-core`

**Status:** Proposed (2026-11-13) · **Owner:** SA/TL · **Supersedes nothing** (additive planning).

## Why this exists

The ktayl insurance Information System (IS) was built **microservices-first**: the core value chain
was split into independent, polyglot services (Policy = Go, Underwriting = Python, Claims = Java,
IAM = NestJS). For a tightly-coupled core under one solo owner, that bought the cost of distribution
(network, polyglot tax, a distributed monolith on the bind path) without the benefit it's meant to pay
for (independent teams/scaling). The correction — already the org rule in
`architecture-strategy.md` (*modular-monolith-first*) — is to make **`ktayl-core` (Java 21 + Spring
Modulith) the default home for insurance business domains as modules**, and to migrate existing
services **only where evidence justifies it**.

> **The governing principle (do not lose this):** the goal is **not** to minimise the number of
> services. It is **clear business ownership, controlled dependencies, reliable transactions,
> independent evolution where it's needed, and manageable operations.** Microservices were not
> automatically a mistake — the question is whether a given distribution is justified by the real
> requirements. See [ADR-004](architecture/adr/ADR-004-si-modular-monolith-target.md).

## Hard rule for this programme
**No destructive change — no code deletion, no service teardown, no prod consolidation — until the
relevant phase's acceptance gate is passed and the ADRs below are ratified.** This roadmap is the
*plan*; it authorises **analysis and new-build**, not demolition.

## The phases (each gated by evidence)

| Phase | Goal | Exit gate (must pass before the next) |
|---|---|---|
| **0 — Freeze & inventory** | A verified baseline: every deployment, repo, DB, API, event flow, consumer, owner. Prohibit destructive change during review. | The committed [Phase-0 inventory](phase-0-inventory.md) is accurate + a "no-destruction" freeze is in force. |
| **1 — Domain & dependency mapping** | The bounded-context map: aggregates, module contracts, which data each module owns; modules vs justified-services vs adopted-COTS. | [ADR-005](architecture/adr/ADR-005-bounded-contexts-and-module-ownership.md) ratified; each domain classified. |
| **2 — `ktayl-core` foundation** | Spring Modulith scaffold + `ApplicationModules.verify()` in CI, Flyway per-schema migrations, Authentik auth, observability, the CI/CD golden path. | Module-verification test green in CI; a schema-per-module skeleton exists; auth + telemetry wired. |
| **3 — First vertical slice (GREENFIELD)** | ONE end-to-end business workflow **in a new, not-yet-built domain** (candidate: **Complaints #30** or **Distribution #13**) — frontend → API → domain → persistence → domain event → security → tests. **Proves the pattern with ZERO prod risk.** | The slice ships to dev, passes the full test pyramid + the live QA gate, and demonstrates module/event/schema isolation. |
| **4 — Claims migration ASSESSMENT** | A written cost/risk/benefit comparison of folding Claims (Java) into `ktayl-core` vs leaving it. **Assessment, not migration.** | A decision record: proceed **only if** evidence shows net value (don't migrate a healthy service for tidiness). |
| **5 — Incremental strangler migration** | Where justified (per Phase 4 + ADR-008): migrate **one capability at a time** — explicit routing, data-ownership transfer, compatibility tests, reconciliation, rollback. | Each migrated capability passes parity + reconciliation + a tested rollback before the next starts. |
| **6 — Rationalization** | Remove superseded scaffolds, deployments, repos, DBs — **only after** the replacement passes functional + operational + recovery acceptance. | The old artefact is provably unused for a soak window; backups/restore verified; then (and only then) retire. |

## Decisions to ratify before Phase 2 (the ADRs)
Recorded under `docs/architecture/adr/` (ADR-001..003 already exist for stack / Stripe-SEPA /
underwriting-ingest; the new set starts at 004):

| ADR | Decision |
|---|---|
| [ADR-004](architecture/adr/ADR-004-si-modular-monolith-target.md) | `ktayl-core` modular monolith is the **default** for new core business capabilities; existing-service migration is **conditional on evidence**. |
| [ADR-005](architecture/adr/ADR-005-bounded-contexts-and-module-ownership.md) | Bounded-context boundaries + what each module **owns** (API / internal / events). |
| [ADR-006](architecture/adr/ADR-006-schema-ownership-and-transactions.md) | PostgreSQL **schema-per-module** ownership + transaction/consistency strategy. |
| [ADR-007](architecture/adr/ADR-007-internal-events-vs-nats-integration.md) | **Internal** Spring application events vs **NATS** integration events (+ transactional outbox). |
| [ADR-008](architecture/adr/ADR-008-service-extraction-criteria.md) | When a module **earns** extraction to a service (the evidence test). |
| [ADR-009](architecture/adr/ADR-009-strangler-migration-and-rollback.md) | Strangler migration mechanics + rollback/reconciliation policy. |
| [ADR-010](architecture/adr/ADR-010-background-jobs-and-idempotency.md) | Background-job execution model under N replicas + idempotency for money/integration ops. |

## The target shape (reference)
```
Next.js (one app / few BFFs)
        │
  ktayl-core  (Java 21 + Spring Modulith) — ONE deployable app, 2–3 replicas, ONE CNPG (schema-per-module)
    modules: Party/Customer · Distribution · Product&Policy · Underwriting · Claims ·
             Billing&Finance · Reinsurance · Risk-Eng · Intl-Programs · Compliance&Audit ·
             Documents · Complaints · Submission · Procurement
        │
  JUSTIFIED SATELLITES (separate, with a real reason):
    ktayl-iam (identity, all apps use it) · AI services (Python/GPU) · legacy-CDC bridge · Retrieva (a product)
  ADOPTED COTS: ERPNext (GL/HR) · GLPI (ITSM) · Workplace suite · Investment/ALM engine
  INTEGRATION: NATS (events) + ingress-nginx/Authentik (gateway) + n8n + a few ACL adapters  (NOT an ESB)
        │
  PLATFORM (already built): k3s · ArgoCD · Kargo · CNPG · Vault/ESO · Prometheus/Grafana/Loki/OTel
```

## Explicitly NOT in scope (yet)
- Rewriting Policy (Go) or Underwriting (Python) — **Phase 4+ only, evidence-gated.** They may stay
  permanently (Python pricing math; Go throughput) — that is a legitimate outcome, not a failure.
- Deleting any repo/service/DB — **Phase 6 only, after acceptance.**

Related: `architecture-strategy.md` (org rule), `phase-0-inventory.md` (baseline), the ADRs above.
