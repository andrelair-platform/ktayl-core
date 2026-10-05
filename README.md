# ktayl-core

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-blue)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen)](https://spring.io/projects/spring-boot)
[![Spring Modulith](https://img.shields.io/badge/Spring%20Modulith-1.3-green)](https://spring.io/projects/spring-modulith)
[![Supply chain: cosign](https://img.shields.io/badge/supply%20chain-cosign%20signed-green)](https://github.com/sigstore/cosign)

> The ktayl-solution **insurance-LOB modular monolith** — a single Spring Boot application whose
> business domains are **Spring Modulith modules** (one deployable, one Postgres, schema-per-module),
> running on the self-hosted ktayl-solution Kubernetes IS. It integrates with the existing platform
> (the live **PAS** `ktayl-policy-service`, **ERPNext** GL, **Authentik** SSO) rather than rebuilding
> it. First module: **Billing** — the premium-to-cash link (bound policy → invoice → payment → GL).

**Live docs:** https://andrelair-platform.github.io/ktayl-core/ *(added with the first docs slice)*
**Platform docs:** https://andrelair-platform.github.io/minicloud-platform-docs/

---

## Why a modular monolith (not N services)

Per `architecture-strategy.md` (modular-monolith-first): a new insurance business domain is a **module
here**, not a new repo/service/board/pipeline. Boundaries are designed from day one (own interface + own
schema) so a module can later be *extracted* to a service **if** the extraction test justifies it — but
it is not deployed as one by default. For a solo dev this avoids the per-service tax (CI + Kargo + CNPG
+ Helm + netpols + QA gate × N) while keeping clean domain seams. Boundaries are **enforced** by a
Spring Modulith `ApplicationModules.verify()` test, not left to discipline.

## Modules

| Module | Status | Responsibility |
|---|---|---|
| **billing** | 🏗️ MVP in build | Premium invoicing + installments + payments (**Stripe test, SEPA Direct Debit**, webhook-driven); posts double-entry **Journal Entries to ERPNext GL** (adopts the ledger, does not rebuild it). Consumes bound policies from the live PAS. |
| distribution / crm | planned | Next module — broker/CRM intake front door |

## Architecture

```
Authentik SSO ─▶ Ingress ─▶ ktayl-core (Spring Boot, one pod set)
                                 │  src/main/java/com/ktayl/core/
                                 │    billing/      (module: api → domain → persistence, schema `billing`)
                                 │    shared/       (cross-cutting: config, security, integration clients)
                                 ▼
   PolicyClient ─────────▶ ktayl-policy-service (PAS, live)   [bound policy: premium, holder, inception, LOB]
   LedgerClient ─────────▶ ERPNext API (GL, live)             [Journal Entry: receivable / premium income / cash]
   PaymentGatewayClient ─▶ Stripe (TEST, SEPA DD)             [PaymentIntent] ──webhook──▶ /webhooks/stripe (signed)
                                 ▼
                         PostgreSQL (one DB, schema-per-module)
```

| Component | Choice |
|---|---|
| Runtime | Java 21 (LTS) |
| Framework | Spring Boot 3.4 + **Spring Modulith 1.3** (module boundaries + application events + verification) |
| Build | Maven |
| Persistence | Spring Data JPA + **Flyway** (migrations per module), PostgreSQL schema-per-module |
| Integration | `RestClient` → PAS + ERPNext + Stripe (test, SEPA DD; contract-tested) + a signed inbound webhook |
| AuthN | Authentik OIDC (resource-server; the console/portal is SSO-gated) |
| Registry / GitOps | ghcr (prod) + Harbor (dev) · ArgoCD · **Kargo git-Warehouse** (JVM base-layer date breaks NewestBuild) |
| Deploy | GAP wrapper chart `minicloud-gitops/services/ktayl-core/helm/`, ns `ktayl-core` / `ktayl-core-prod` |

## Getting Started

```bash
./mvnw spring-boot:run        # local (needs a Postgres; see docker-compose for L2)
./mvnw test                   # L0+L1 (unit) + the ModularityTests boundary verification
./mvnw verify                 # + L2 integration (Testcontainers Postgres)
```

## Status

Path-C product in planning→build. Planning artefacts: `docs/prd.md`, `docs/architecture.md`,
`docs/architecture/adr/`, `docs/specs/spec-billing-mvp/`. Board: GitHub Project (IS/Insurance LOB).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) — trunk-based, GPG-signed Conventional Commits, module discipline,
full test layers in the Definition of Done.

## License

[MIT](LICENSE)
