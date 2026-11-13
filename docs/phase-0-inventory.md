# Phase-0 inventory — ktayl insurance SI baseline (2026-11-13)

**Status:** Baseline for the [modernization roadmap](modernization-roadmap.md). Verified by scanning the
repos + `minicloud-gitops` ArgoCD apps. **This is the "do-not-destroy" freeze baseline** — nothing in the
BUILT or ADOPTED columns may be torn down until its replacement passes the Phase-6 acceptance gate.

## A. BUILT — real custom code, live
| Domain | Repo | Stack | Live | Target (provisional — confirm in Phase 1/4) |
|---|---|---|---|---|
| Policy Admin (PAS) | `ktayl-policy-service` | Go / gRPC | dev + **prod** | **keep initially**; fold → module only if Phase-4 evidence justifies (expensive: Go→Java) |
| Underwriting & Pricing | `ktayl-underwriting` | Python / FastAPI (+ Next BFF) | dev + **prod** | **keep initially**; pricing compute may stay a Python service permanently |
| Claims | `ktayl-claims` | Java / Spring | dev + **prod** | **Phase-4 migration candidate** (same stack); keep only the legacy-CDC bridge |
| IAM / IGA | `ktayl-iam` | NestJS + Next | dev + **prod** | **keep as a service** (identity consumed by all apps — justified) |
| Billing (premium→cash→GL) | `ktayl-core` | Java / Spring Modulith | repo built, early | **this IS the monolith** — the destination |
| Retrieva (DORA 3rd-party-ICT) | `retrieva` + `retrieva-assessment-crew` | Node / TS | dev + **prod** | **keep separate — a PRODUCT (cert deliverable), not an IS domain** |

## B. ADOPTED (COTS, live) — never build
| Domain | Tool | Repo/board |
|---|---|---|
| ITSM | GLPI | `ktayl-itsm` (#16), live dev |
| Digital Workplace | Nextcloud / Stalwart / Matrix / Jitsi / Docuseal / n8n | `ktayl-workplace` (#10) |
| ERP / GL / HR | ERPNext | `minicloud-erpnext` (#8) |
| Data Platform / BI | dbt + CNPG + Metabase | `ktayl-data-platform` (#5), platform-side |

## C. SCAFFOLD / NOT-BUILT — decision list (0 real business code today)
All default to **`ktayl-core` module** unless marked otherwise. Confirm in Phase 1 (ADR-005).
| Domain | Repo / Board | Provisional target |
|---|---|---|
| Distribution & CRM | `ktayl-distribution` #13 | module (Phase-3 greenfield candidate) |
| Insurance Finance (IFRS17/reserving) | `ktayl-finance` #14 | module (GL stays ERPNext) |
| Regulatory & Compliance (workflow) | `ktayl-compliance` #15 | module (obligations register = content) |
| Reinsurance | `ktayl-reinsurance` #22 | module |
| Risk Engineering | `ktayl-risk-engineering` #21 | module |
| International Programs | `ktayl-international-programs` #23 | module |
| Customer / MDM | `ktayl-mdm` #20 (parked, some code) | module — **shared kernel** |
| Documents & DMS | `ktayl-dms` #24 | module (OCR/IDP/generation) + adopt Paperless/Nextcloud/Docuseal |
| Integration Platform | `ktayl-integration` #25 | **NOT an ESB** — NATS + gateway + a few ACL adapters (see ADR-007) |
| Submission Hub | `ktayl-submission` #27 (to create) | module |
| Complaints & Disputes | `ktayl-complaints` #30 (to create) | module (Phase-3 greenfield candidate) |
| Procurement & CLM (business vendors) | `ktayl-procurement` #31 (to create) | module or adopt |
| Investment & Treasury / ALM | `ktayl-investment` #29 (to create) | **ADOPT** specialist engine — never hand-build |

## D. Justified satellites (stay separate — real force, not fashion)
1. `ktayl-iam` — company-wide identity/IGA, consumed by all, independent lifecycle.
2. Legacy-CDC bridge (claims ↔ frozen `globalcore`) — adapter over an external/legacy system.
3. AI services (`minicloud-agent` / `minicloud-crew-agent`, Python/GPU, RAG) — different stack + scaling.
4. Pricing/rating compute (Python) — **evaluate** (Phase 4): compute service the UW module calls, or a library.
5. `retrieva` — a separate product (the RNCP39583 cert), not an IS domain.

## E. Open questions to resolve in Phase 1
- **Customer/Party ownership:** today customer refs live inside `ktayl-policy-service` + distribution;
  the golden-record MDM (#20) is a gap. Decide the shared-kernel vs owning-module model (ADR-005).
- **Frontend consolidation:** per-service Next.js BFFs today → one app / few BFFs in target. Scope later.
- **Claims legacy coupling:** map the `globalcore` CDC/ACL surface before any Claims migration (Phase 4).
- **Data ownership per domain:** which tables each module owns + the no-cross-join policy (ADR-006).

## Non-destruction freeze (in force during the review)
Until the roadmap's phase gates are passed: **do not** delete a repo/service/DB, tear down a deployment,
or consolidate prod. New work is allowed (new modules in `ktayl-core`); demolition is not.
