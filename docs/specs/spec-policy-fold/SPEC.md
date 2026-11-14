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
| **FR — BF-POL-04** | List policies with filters + cursor pagination | 1b |
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
Coverages (eurocents) · premium-metadata · documents + **attestation PDF** (MinIO, ACPR) · the remaining
lifecycle endpoints (amend/confirm/reject/expire-batch) · list+pagination (BF-POL-04) · lifecycle events
(in-process + the external `policy.activated` via the ktayl-core transactional-outbox pattern).

### Phase 2 — backfill
One-off job: copy the existing policies (+ coverages/documents) from the Go PAS DB into the `policy` schema.
Binds are idempotent by `policy_number`, so a clean cutover works; dual-write is optional.

### Phase 3 — repoint underwriting
Flip `policy_service_url` → the ktayl-core policy endpoint. Align the write-authz (underwriting's bind token
→ a `policy:write` / group authority ktayl-core accepts). Underwriting stays a separate Python service — only
its target changes. **Verify:** a UW bind flows create→submit→activate into ktayl-core, policy ACTIVE + audited.

### Phase 4 — soak + Phase-6 acceptance gate
Parity (policy counts/states match the Go PAS), no data loss, the live QA gate, SLO check.

### Phase 5 — retire the Go policy-service
Only after the gate passes (non-destruction freeze, ADR-004): delete the ArgoCD app + the repo; this SPEC +
ADR-015 remain the design of record.

## Out of scope / deliberate
Premium accounting (ERPNext + billing) · multi-tenant · PDF digital signature (Docuseal, later) · the
EXPIRED batch job (a CronJob, Phase 1b+). These mirror the PAS's own out-of-scope decisions.
