# SPEC — Policy fold (ktayl-policy-service → ktayl-core `policy` module)

**Status:** Phase 1 in progress · **Owner:** SA/TL · **Decision:** ADR-014 (fold) + ADR-015 (design).
**Why this doc exists:** ktayl-policy-service (incl. its `_bmad`) is **deleted** after the fold — this SPEC
is the committed implementation contract that carries its requirements (`BF-POL-*`) + design forward.

## Requirements carried forward (from the PAS PRD, CdCF §6.1)
| Ref | Requirement | Phase |
|---|---|---|
| **FR — BF-POL-01** | Create a policy (holder, product_code, effective/expiry dates[, coverages]) | 1 (coverages → 1b) |
| **FR — BF-POL-02** | Enforce the state machine DRAFT→SUBMITTED→ACTIVE→AMENDED→CANCELLED/EXPIRED/REJECTED | 1 (amend/reject/expire endpoints → 1b) |
| **SEC — BF-POL-03** | Every transition writes an immutable audit row (DORA Art.9): actor_id + from→to + reason + timestamp | **1 (done)** |
| **FR — BF-POL-04** | List policies with filters + cursor pagination; update a DRAFT policy | **done** |
| **FR — BF-POL-05** | Attestation PDF on demand (ACPR Art.L113-5) → MinIO + presigned URL | 1b |
| **AI/INT — BF-POL-06** | Lifecycle events on every transition (in-process for modules; external `policy.activated` NATS for off-monolith) | 1b |
| **SEC — BF-POL-07** | All endpoints Authentik-JWT-protected (except health); actor audited | **1 (done)** |
| **FR — BF-POL-08** | Soft-cancel (→CANCELLED) with a mandatory reason code | **1 (done)** |

**NFRs (carried):** p99 < 200 ms CRUD; DORA Art.9 non-repudiable audit; ACPR 7-yr attestation retention;
structured logs + health; 70% domain coverage. Premium = **metadata only** (ERPNext owns accounting; the
premium→cash→GL chain is billing's, off the Underwriting bound-risk event — not re-implemented here).

## The coupling seam (the only consumer)
**ktayl-underwriting** at bind, 4 calls (`app/bind/policy_client.py`): `POST /v1/policies` (409→resolve),
`GET /v1/policies?policy_number=`, `POST /{id}/submit`, `POST /{id}/activate`. Claims / billing / ktayl-core
do **not** call the PAS. ⇒ repoint = one base-URL change (`policy_service_url`).

## Phases (ADR-009 strangler — non-destructive until each gate)

### Phase 1 — bind-path core + audit ✅ (this increment)
- `policy` schema (V10): `policy.policy` + `policy.policy_audit_log`.
- Hexagonal module (ADR-013): `domain` (Policy aggregate + state machine + ports, PURE) · `application`
  (PolicyService + PolicyAuditLog port) · `infrastructure/{persistence,web}` · `api` (`PolicyQuery` named interface).
- REST: `POST /v1/policies` (201 / 409), `GET ?policy_number=` (PAS **list** envelope `{"items":[…]}`, always
  200 — so underwriting's 409-resolve filters client-side), `GET /{id}` (single object, 404-on-missing),
  `POST /{id}/submit|activate|cancel`.
- DORA audit on every transition; actor from the token.
- Tests: L1 state machine (done), **L2** real-Postgres bind flow + audit + 409 + resolve, **L3** API contract.
- **Gate:** CI green; the module is additive — the Go PAS is untouched.

### Phase 1b — the rest of the PAS surface
- **DONE — list + pagination + update (BF-POL-04):** `GET /v1/policies?status=&cursor=&limit=` (keyset on
  `(created_at,id)`, opaque base64 cursor carried forward from the PAS, `{items,next_cursor}` envelope) +
  `PUT /v1/policies/{id}` (DRAFT-only edit, audited). The keyset uses **two queries** (first-page / after-cursor)
  so a nullable timestamp never lands in an `IS NULL` context — Postgres can't infer its type there.
- **DROPPED — coverages + premiums (fold simplification, documented decision):** both tables existed in the
  PAS schema but had **no write path** (the PAS create took only holder/product/dates; the router had **no**
  coverage/premium endpoints) — they were orphan schema. Premiums would also duplicate **billing** (the SoR
  via the UW bound-risk event → invoicing → GL). So neither is carried into the module.
- **Remaining:** documents + **attestation PDF** (MinIO, ACPR Art.L113-5 — BF-POL-05, infra: PDF lib + MinIO +
  ESO/Vault creds + egress netpol) · the other lifecycle endpoints (amend/confirm/reject/expire-batch) ·
  lifecycle events (in-process + the external `policy.activated` via the ktayl-core transactional-outbox).
  All **need-first** — built when a consumer exists (attestation is cert-valuable even dormant).

### Phase 2 — backfill ✅ (done on dev; the tool Phase 4 reuses for prod)
One-off, idempotent copy of the existing PAS policies into the `policy` schema — tool:
**`scripts/backfill-policies.sh <dry-run|apply>`** (runs on the controller; source/target overridable by env).
It EXPORTs+TRANSFORMs in the source pod (status → the ktayl-core enum via a CASE map, an unknown status → a
loud failure not a silent mismap; dates TIMESTAMPTZ→DATE; **coverages/premiums NOT carried** — orphan/billing),
IMPORTs via a TEMP-table `\copy` + `INSERT … ON CONFLICT (policy_number) DO NOTHING` (idempotent, IDs+timestamps
preserved), and writes **one BACKFILL audit row per policy** (DORA Art.9, idempotent).
- **Dev run (2026-11-15):** 9 PAS policies (7 active / 1 cancelled / 1 draft) → all mapped + inserted; re-run
  = `INSERT 0 0`; a backfilled policy (`UW-369188ABE405`) now resolves `200 ACTIVE` via the API and a
  duplicate create returns `409` → the dev split-brain is gone, the 409-resolve bind path is consistent.
- **Phase 4 prod:** point the env at the prod source/target (the Go PAS prod is on CNPG `ktayl-cnpg`) — same tool.

### Phase 3 — repoint underwriting (dev) ✅
`POLICY_SERVICE_URL` → ktayl-core; auth via the multi-issuer allowlist (ADR-016). Verified: a UW bind flows
create→submit→activate into ktayl-core, policy ACTIVE + audited. Underwriting stays a separate Python service.

### Phase 4 — prod cutover ✅
Promote ktayl-core→prod (Kargo CODEOWNERS PR) + QA gate (L5, found+fixed the oversized-input 500) + backfill
prod + **soak** + repoint prod underwriting. Verified: a real prod bind lands in ktayl-core-prod (201/204/204/200,
ACTIVE + DORA audit). The Go PAS prod stays running (idle) = the rollback path.

### Phase 5 — retire the Go policy-service (GATED — non-destructive until ALL criteria pass, ADR-004)
> ⚠️ **Do NOT delete anything until the acceptance gate below is fully green.** The Go PAS is the rollback
> path; retirement is a one-way action. Minimum **prod soak ≥ 1 week** of ktayl-core-prod serving the live
> bind path before this gate is even evaluated.

**Phase-6 acceptance gate (every box must be ✅ before any deletion):**
- [ ] Prod soak ≥ 1 week: ktayl-core-prod policy module healthy (no crashloops/restarts, SLO met), the live
      QA gate re-run clean, prod complaints + billing SSO unaffected.
- [ ] **Parity**: `policy.policy` row count/states in ktayl-core-prod ≥ the Go PAS prod `policies` (no data loss);
      every Go-PAS policy_number resolvable via the ktayl-core API.
- [ ] Zero prod traffic still hitting the Go PAS (confirm no callers remain — grep every `POLICY_SERVICE_URL`;
      underwriting-prod repointed; no other consumer).
- [ ] A final backfill re-run (`backfill-policies.sh apply`, idempotent) to catch any policies the Go PAS
      created during the soak window.

**Retirement runbook (only once the gate is green):**
1. `kubectl --context minicloud -n ktayl-prod scale deploy/ktayl-policy-service --replicas=0` (quiesce; keep the
   manifests a few days as a fast un-retire).
2. Remove the Go PAS ArgoCD app(s) + Kargo project + its `services/ktayl-policy-service/` from `minicloud-gitops`
   (CODEOWNERS PR) → ArgoCD prunes the deployment, svc, ingress, CNPG app (keep the CNPG **backup** until the repo
   is archived).
3. Archive (don't hard-delete) `andrelair-platform/ktayl-policy-service`; retire board #6 scope into #28.
4. Drop the dev `ktayl-postgres` STS + prod `ktayl-cnpg` policy DB **last**, after a final snapshot.

This SPEC + ADR-015 remain the design of record after the repo is gone.

## Out of scope / deliberate
Premium accounting (ERPNext + billing) · multi-tenant · PDF digital signature (Docuseal, later) · the
EXPIRED batch job (a CronJob, Phase 1b+). These mirror the PAS's own out-of-scope decisions.
