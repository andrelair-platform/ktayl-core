# SPEC — Complaints slice (epic CMPL-01): the Phase-3 greenfield vertical slice

Compact per-epic implementation contract. Derived from the ratified SI modernization plan
(`docs/modernization-roadmap.md` **Phase 3**) + ADR-004..011. Epic owner: TL/BE. Board: ktayl Complaints
(#30), built **as a module in ktayl-core** (#28) — not a new service (ADR-004). Build only after the
readiness gate below.

## Why this domain, why now (the point of the slice)
Complaints & Disputes is a **real regulatory obligation** (ACPR/EIOPA/IDD: acknowledge within statutory
deadlines, investigate, resolve, root-cause report) **and** it is **greenfield** — 0 lines today, no live
service, **zero prod risk**. That makes it the ideal first proof of the ratified architecture: it
demonstrates the *entire* correction — a new business domain shipped as a **module**, not a polyglot
service — end-to-end, without touching anything in production.

## Epic outcome
A complaint can be **registered → acknowledged → assigned → resolved → closed** through a published module
API, persisted in an **owned `complaints` schema**, emitting a **published domain event**
(`ComplaintRegistered`) — all inside `ktayl-core` as a **CLOSED Spring Modulith module** whose boundaries
are **enforced in CI** by `ApplicationModules.verify()`. Demonstrated end-to-end on dev, QA'd.

## What this slice PROVES (map each to the architecture decision)
1. A new domain = a **module** (`com.ktayl.core.complaints`), not a new repo/service (**ADR-004**).
2. **Schema-per-module** ownership — owns `complaints`, reads no other schema (**ADR-006**).
3. The **`api/internal/events`** boundary with a published interface + a published event (**ADR-005**).
4. A **published domain event** as an **in-process Spring application event** — **no NATS** (nothing
   external consumes it yet; this is the ADR-007 "don't NATS-between-modules" discipline made concrete).
5. The **boundary guard in CI** — `verify()` fails the build on any cross-module internal access.

## Module shape (ADR-005, matches the billing convention)
```
com.ktayl.core.complaints/            # @ApplicationModule(displayName = "Complaints") — CLOSED
├── package-info.java                 # the @ApplicationModule marker
├── api/            # PUBLISHED: ComplaintOperations (commands), ComplaintSummary (read DTO), ComplaintStatus
├── events/         # PUBLISHED: ComplaintRegistered (domain event)
└── internal/       # PRIVATE — no other module may reference:
    ├── domain/     #   Complaint aggregate (state machine + invariants), ComplaintChannel
    ├── application/#   ComplaintService (@Transactional ops, publishes the event)
    ├── persistence/#   ComplaintRepository (JPA), ComplaintEntity, Flyway
    └── web/        #   ComplaintController (REST, SSO-gated)
```
Owns **schema `complaints`** (Flyway `V4__baseline_complaints_schema.sql`). No cross-schema reads; any
party/policy reference is **by identifier** (shared-kernel read later — out of scope here, ADR-005/006).

## Domain (the minimal, real workflow)
Aggregate **`Complaint`** with a guarded state machine:
`REGISTERED → ACKNOWLEDGED → ASSIGNED → RESOLVED → CLOSED`. Invariants: transitions are ordered (no skip,
no backward); `acknowledge` sets an **acknowledgement-due timestamp** (`acknowledgementDueAt`) — MVP =
`registeredAt + N calendar days` from config, an explicit **placeholder** (the *real* ACPR/EIOPA deadline is
business-day + jurisdiction-specific — deferred, not claimed here); `assign` requires an **`assignedTo`**
(user/team ref); `resolve` requires a non-blank resolution note; a terminal (`CLOSED`) complaint rejects
further operations (409). Every transition is recorded in the module's **append-only `audit_log`** (in the
`complaints` schema — the module owns its audit, per Billing's convention).

## Stories

### CMPL-010 — Complaints module skeleton + owned schema + boundary guard · infra · P1 · 5
Add `com.ktayl.core.complaints` as a **CLOSED** `@ApplicationModule` (package-info); Flyway
`V4__baseline_complaints_schema.sql` creates schema `complaints` + the `complaint` table; a thin
`ComplaintController` under `/api/complaints` (unauthenticated → 401).
- **AC** ✓ app boots with the new module; ✓ **`ApplicationModules.verify()` still passes** (the existing
  `ModularityTests` auto-discovers it); ✓ Flyway creates `complaints`; ✓ `/api/complaints` is 401 unauth.
- **AC (fail)** ✗ a planted reference from `complaints.internal` into `billing.internal` makes `verify()` FAIL
  (prove the guard bites across the *new* module); revert before commit.
- **DoD** L0+L1 green incl. the modularity test; no change to the `billing` module.

### CMPL-011 — Complaint aggregate + register + the published event · domain · P1 · 5
The `Complaint` aggregate (REGISTERED state), `ComplaintService.register(cmd)`, persistence, and the
**published `ComplaintRegistered` event** (Spring `ApplicationEventPublisher`, in-process).
`complaint` table (schema `complaints`): `id (uuid pk) · status · channel · subject_ref (party/policy id,
nullable) · summary (text) · registered_at · acknowledgement_due_at (null until ack) · assigned_to (null) ·
resolution_note (null) · created_by/updated_by (token actor)`. Plus a per-schema append-only `audit_log`
(matches Billing's convention — the module owns its audit).
- **AC** ✓ `register(channel, subjectRef, summary)` persists a `complaint` row (status REGISTERED, uuid id,
  registeredAt) and **publishes `ComplaintRegistered`** in the same transaction; ✓ the event carries
  `{complaintId, channel, registeredAt}` (published type, no internals leaked); ✓ a test in-module listener
  receives it (proves *publication* — a real cross-context consumer is later, see non-scope); ✓ **safe-retry
  idempotency via an explicit `Idempotency-Key` request header** (same key → the same complaint, not a second
  row) — this is POST-retry safety, **NOT** content-dedup: two genuinely different grievances (different keys)
  are two complaints.
- **AC (fail)** ✗ missing/blank summary → 4xx (validation), not 5xx; ✗ **same `Idempotency-Key` replay → the
  same row** (no duplicate); ✓ **absent** key → each call is a new complaint (a person may file more than one).
- **L1** aggregate/state tests; **L2** real Flyway/JPA persistence. **DoD** a register call → one row + one event.

### CMPL-012 — Lifecycle operations + state-machine guards + ack-due clock · domain · P1 · 5
`acknowledge → assign(assignedTo) → resolve(note) → close` on `ComplaintService`, each guarded by the
aggregate's state machine.
- **AC** ✓ each transition only from its legal predecessor (else **409**); ✓ `acknowledge` sets
  `acknowledgementDueAt` (the placeholder clock — not claimed as the statutory deadline); ✓ `assign` requires
  a non-blank **`assignedTo`** (else 4xx) and records it; ✓ `resolve` requires a non-blank resolution note
  (else 4xx); ✓ operating on a `CLOSED` complaint → 409; ✓ **every transition writes an append-only
  `audit_log` row** (actor from token, from→to, when).
- **AC (fail)** ✗ out-of-order (e.g. resolve before acknowledge) → 409; ✗ re-close → 409; ✗ unknown id → 404;
  ✗ `assign` with blank `assignedTo` → 4xx.
- **L1** state-machine (legal + illegal transitions) + boundary (`<=` vs `<` on the ack-due comparison). **DoD**
  full lifecycle green + an audit row per transition.

### CMPL-013 — Published API + REST + SSO security · api · P1 · 3
`ComplaintOperations` (published command interface) + `ComplaintSummary` (read DTO) in `api/`; the
`ComplaintController` maps them; Authentik OIDC resource-server (reuse `shared/config`).
- **AC** ✓ `POST /api/complaints` (register), `POST /api/complaints/{id}/{ack|assign|resolve|close}`,
  `GET /api/complaints/{id}` (returns `ComplaintSummary`, never the entity); ✓ all `/api/**` require a valid
  token → **401** unauth; **authz = authenticated + the `Complaints Handler` Authentik group** (a group
  claim check) → **403** without it (don't invent per-endpoint scopes for the proof; one group gate is
  enough and real); ✓ the **actor** is taken from the token, not a placeholder (audit attribution — the
  ktayl-underwriting lesson); ✓ `openapi.yaml` committed.
- **AC (fail)** ✗ malformed JSON / wrong content-type → 4xx, no 5xx; ✗ not-found id → 404.
- **L3** contract test of the API shape; **L4** smoke (register→ack→close round-trip over the dev ingress).
- **DoD** committed `api/openapi.yaml`; L3 green.

### CMPL-014 — Deploy to dev + the live QA gate · devops/testing · P1 · 5
Deploy the new ktayl-core image (same single deployable — Complaints adds no new service) to **dev**; run
the adversarial **QA agent** pass against the live module (`qa-gate.md`).
- **AC** ✓ ktayl-core dev app Synced/Healthy with the Complaints endpoints live; ✓ the QA gate's 12 coverage
  areas pass (happy paths, validation→4xx, not-found→404, **state guards→409**, idempotency, authz,
  observability: **the full triad** — structured logs emitted to stdout, a **trace appears in Tempo** for a
  register→close flow, and metrics scrape works (ADR-012), no 500s); ✓ blockers fixed + re-verified on the converged pod.
- **DoD** QA pass clean (or triaged); the vertical slice demonstrably runs end-to-end on dev. (Prod promotion
  is a **later** CODEOWNERS/Kargo step — not in this slice.)

### CMPL-020 — As-built doc + ERD + org-site pointer · docs · P2 · 2
- **AC** ✓ `complaints` tbls ERD under `docs/data-model/` auto-generated (schema-erd CI); ✓ a short as-built
  page + the slice's "pattern proven" note on the central docs hub (pointer); ✓ traceability matrix row.
- **DoD** docs build-checked; the slice is discoverable on the hub. (Documentation is a DoD gate, not a follow-up.)

## Sequence
CMPL-010 → CMPL-011 → CMPL-012 → CMPL-013 → CMPL-014 → CMPL-020. ≈25 pts — one focused sprint for the
proof slice.

## Explicitly NOT in scope (keep the proof small — need-first)
- No SLA/escalation automation, no ombudsman integration, no AI root-cause, no regulatory-report generation.
- **No NATS / no cross-module consumer** — `ComplaintRegistered` is *published*, proving **publication**
  (a test listener confirms it fires). It proves **half** the event story — the safe half; the full
  cross-context *reaction* (a future Compliance module consuming it — ADR-007) is deliberately later.
  Publishing now is the point; consuming is a follow-up slice.
- No UI beyond the REST API. No party/policy shared-kernel wiring (reference by id only).
- No prod promotion in this slice (dev + QA gate only).

## Readiness gate
| Check | Verdict |
|---|---|
| Business need grounded | ✅ Complaints is a real ACPR/EIOPA/IDD obligation + a known blind-spot (#30); greenfield, 0 prod risk |
| Stack decided + justified | ✅ ADR-001 (Spring Boot + Spring Modulith) — the module inherits it |
| Architecture + boundary | ✅ ADR-005 (api/internal/events) · ADR-006 (schema `complaints`) · ADR-007 (in-process event, no NATS) |
| Fits the modular monolith | ✅ ADR-004: a new domain = a module in ktayl-core; no new repo/service/pipeline |
| Boundary enforcement | ✅ existing `ModularityTests` auto-covers the new module; CMPL-010 AC(fail) proves the guard bites |
| Test discipline | ✅ Tier-A layers in the DoD (L1 state-machine · L2 real Flyway/JPA · L3 API contract · L4 smoke) + L5 QA gate |
| NFR/security/compliance | ✅ SSO-gated, token-derived actor, append-only audit, schema-isolated, **money-free / no PSP / no external egress / no cross-service contract** (lower risk than Billing). ⚠️ **but it IS personal data** — see the privacy note below. |
| Governance gate | ⚠️ **SA review + a LIGHT privacy check** (NOT "no SEC"): a complaint is **personal data** (GDPR) — `summary`/`subjectRef` can carry sensitive content. Controls in scope: SSO gate · token-derived actor · append-only audit · schema isolation. **Retention/erasure (GDPR Art. 17) is flagged, out-of-scope for the proof, owned by Compliance later.** The surface is still low-risk (greenfield · internal · no external boundary · no PSP), so the review is light — but it is **not zero**. |

> **Privacy note (corrects an earlier "no PII" framing).** A complaint is, by definition, a named
> person's grievance — **personal data under GDPR**, occasionally special-category (e.g. a health-claim
> dispute). This slice does **not** make that a blocker (it's greenfield, internal, SSO-gated, audited,
> schema-isolated — the controls a proof needs), but the SPEC must not pretend otherwise: data
> classification = **personal (P2-ish)**, retention/erasure is a **named, deferred** follow-up, and the
> governance gate includes a light privacy check, not "no SEC". Honesty here is the whole point of the exercise.

**Verdict: PASS** — ready to build. Open items (both routine, non-blocking): SA sign-off on the new schema +
public API, and the light privacy acknowledgement above. There is deliberately **no** external boundary,
PSP, or cross-service contract in this slice — that is what makes it the safe first proof.
