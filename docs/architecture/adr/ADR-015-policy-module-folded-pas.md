# ADR-015 — The `policy` module: the PAS folded into ktayl-core (design carried forward)

**Status:** Accepted (2026-11-15) · **Owner:** SA/TL · **Context:** executes ADR-014 (fold policy-service +
claims into ktayl-core), Phase 1. Carries the design forward from **ktayl-policy-service** (whose repo +
`_bmad` planning artifacts are **deleted** once the fold completes), so the policy design survives in
ktayl-core as committed ADR + SPEC rather than in a discarded repo. Source: the PAS PRD (`BF-POL-01..08`,
CdCF §6.1) + its architecture doc.

## Context
ADR-014 folds the policy administration system (PAS) into ktayl-core as a Spring Modulith module. The PAS
(ktayl-policy-service, Go/REST) is the system-of-record for policies: a registry + a controlled lifecycle +
a DORA-Art.9 audit trail + an ACPR attestation document + lifecycle events. Its single consumer is
**underwriting** (bind: create → submit → activate). When the Go service is retired (ADR-014 Phase 5), its
design must live on **here** — this ADR is that record.

## Decision — the policy module design (carried forward from the PAS)
1. **Lifecycle state machine** (BF-POL-02), enforced in a pure domain aggregate (ADR-013):
   ```
   DRAFT ──submit──▶ SUBMITTED ──activate──▶ ACTIVE ──amend──▶ AMENDED ──confirm──▶ ACTIVE
                         │ reject                │ cancel(reason)        ACTIVE ──expire(batch)──▶ EXPIRED
                         ▼                        ▼
                     REJECTED                 CANCELLED
   ```
   Underwriting's bind drives `DRAFT → SUBMITTED → ACTIVE`. Each transition runs only from its legal
   predecessor (else 409). **Cancel requires a reason** (BF-POL-08).
2. **DORA Art.9 audit (BF-POL-03) — a MUST.** Every transition writes one immutable `policy_audit_log` row:
   `policy_id, action, from_status, to_status, actor_id, reason, at`. The actor is the Authentik `sub` /
   `preferred_username` claim. Append-only. This (+ the controlled state machine + attestation) is *why the
   PAS exists as a service rather than ERPNext* — keep it.
3. **Schema-per-module** (ADR-006): the module OWNS the `policy` Postgres schema (`policy.policy`,
   `policy.policy_audit_log`); it never reads another module's tables.
4. **The REST contract is carried forward verbatim** so underwriting repoints with a **base-URL change only**
   (ADR-014 Phase 3): `POST /v1/policies` (201; 409 if exists), `GET /v1/policies?policy_number=` (the PAS
   **list** endpoint — always `200` with a `{"items":[…],"next_cursor":…}` envelope, **never a single object
   or 404**; underwriting's 409-resolve filters this client-side), `GET /v1/policies/{id}` (single object,
   404-on-missing), `POST /{id}/submit|activate|cancel`. *(A Phase-1 fix: the first cut of `GET` returned a
   single object / 404, which broke the 409-resolve idempotency path — corrected to the list envelope by
   reading the real Go handler before repointing the consumer.)*
5. **Published in-process read API** (ADR-005): `policy.api.PolicyQuery` (a `@NamedInterface`) lets other
   modules (billing, claims) read policy data in-process — the **shared-kernel value** of the fold (no
   cross-service HTTP). The internal aggregate/JPA entity never leaks.
6. **Idempotency = deterministic policy_number** (PAS ADR): a duplicate create → 409; the caller resolves.
7. **Premium = metadata only** (PAS out-of-scope): ERPNext owns premium accounting; billing (ktayl-core)
   owns the premium→cash→GL chain off the Underwriting bound-risk event. The policy module does **not**
   re-implement premium/payment.
8. **Auth = Authentik OIDC** (PAS ADR-007; our `workplace-architecture.md`): every endpoint authenticated
   (the actor is audited). *Fine-grained write-scope alignment with underwriting's bind token is a Phase-3
   cutover detail.*

## Carried-forward PAS decisions (kept / adapted)
| PAS decision | In ktayl-core |
|---|---|
| eurocents `int64` for money (ADR-003) | same — eurocents `long` (where money appears, e.g. coverages Phase 1b) |
| DORA Art.9 audit on every transition (BF-POL-03) | **kept** — `policy.policy_audit_log` |
| CloudEvents 1.0 to NATS on transitions (BF-POL-06) | **Phase 1b** — but as an in-process event / the ktayl-core pattern; *no NATS between modules* (ADR-007). External `policy.activated` for off-monolith consumers (AI agent) stays NATS. |
| Attestation PDF, ACPR Art.L113-5 (BF-POL-05) | **Phase 1b** (coverages + documents + PDF + MinIO) |
| NATS-after-commit, no outbox (PAS ADR-004) | re-evaluate: ktayl-core already has the transactional-outbox pattern (BILL-014) — prefer it for the external `policy.activated` event when wired |

## Consequences
- **Positive:** the shared-kernel policy data lives in-process (removes the future billing/claims↔policy
  HTTP seam); the lifecycle + DORA audit + ACPR attestation are preserved; the design outlives the deleted
  Go repo. Cert evidence (BC02/BC03): the state machine + audit + attestation.
- **Negative / accepted:** a Go→Java reimplementation (bounded — thin registry, one consumer); temporary
  dual-running during cutover (ADR-009); premium/coverage/documents are Phase 1b, not Phase 1.
- **Phasing (ADR-009 strangler, non-destructive until each gate):** **Phase 1** bind-path core + audit
  (this) · **Phase 1b** coverages + premium-metadata + documents + attestation + lifecycle events · **Phase
  2** backfill from the Go DB · **Phase 3** repoint underwriting (`policy_service_url` → ktayl-core) ·
  **Phase 4** soak + Phase-6 acceptance gate · **Phase 5** retire the Go service.

Related: ADR-014 (the fold), ADR-004/005/006/009/013, the SPEC `docs/specs/spec-policy-fold/SPEC.md`, the
PAS source PRD/architecture (ktayl-policy-service `_bmad-output/planning-artifacts/`, to be deleted).
