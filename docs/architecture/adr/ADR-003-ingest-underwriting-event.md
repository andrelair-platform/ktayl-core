# ADR-003 — Billing ingest: the Underwriting bound-risk event via JetStream (not a PAS poll)

- **Status:** Accepted (2026-10-05)
- **Owner:** AndreLiar (SA/TL) · **Decided with:** owner, 2026-10-05
- Supersedes the BILL-011 "poll-reconcile from the PAS" assumption in the PRD/architecture draft.

## Context — what grounding the real contracts revealed

The plan assumed Billing reads the bound policy **(incl. premium)** from the PAS
(`ktayl-policy-service`). Reading the live OpenAPI + how Underwriting actually integrates showed:

1. **The PAS is a thin registry.** `PolicyResponse` = `policy_number, holder_name, product_code,
   status, effective_date, expiry_date` — **no premium, no currency, no LOB amount**. "Bound" = status
   **`active`** (the enum is draft/active/suspended/terminated; there is no "bound"). The PAS *is*
   OIDC-authenticated (scope `policy:write`); its OpenAPI `securitySchemes` is stale.
2. **The premium lives in Underwriting** (`premium_minor` eurocents + `currency`, linked by
   `policy_number`; UW's own comment: *"premium/limits/terms stay in the UW record"*).
3. **Underwriting already emits a bound-risk event at bind** (`app/bind/service.py` →
   `app/bind/publisher.py`): subject **`insurance.underwriting.bound-risk`**, payload
   `{policy_number, submission_id, quote_id, premium_minor, currency, product_code, effective_date,
   expiry_date}` — **exactly what Billing needs**.
4. UW's REST reads are keyed by `submission_id` only (no list, no `policy_number` lookup) → a
   PAS-poll-then-pull-premium reconcile is **not possible** with existing endpoints.
5. The event is a **core NATS publish** (not JetStream), and **no stream captures the subject**
   (`POLICY_EVENTS` binds `policy.>` only) → today it is fire-and-forget: a bind while Billing is down
   is lost. Unacceptable for a money chain.

## Decision

**Billing ingests the bound premium from the Underwriting `bound-risk` event, made durable by a new
JetStream stream.**

- Add a JetStream stream **`UNDERWRITING_EVENTS`** (subjects `insurance.underwriting.>`, File storage,
  Limits retention) in gitops — mirroring `HR_LIFECYCLE`. A JetStream stream captures matching subjects
  even from a **core** publish, so **Underwriting needs NO code change**.
- Billing runs a **durable JetStream consumer** (manual ack) on `insurance.underwriting.bound-risk` →
  idempotent-upsert an `ingested_policy` row (keyed by `policy_number`) + audit. This is the proven
  HR_LIFECYCLE durable-consumer pattern.
- **Drop the PAS dependency from the Billing MVP.** The event carries premium + policy_number + dates +
  product_code — everything the invoice needs. `holder_name` (display-only) is **deferred** enrichment
  from the PAS, not required for the money mechanics.

## Why (vs the alternatives)
- **Smallest change + reliable:** one stream manifest, zero UW code change, replayable durable delivery
  (catches events published while Billing was down). Reuses a proven pattern.
- **Rejected — add a UW premium-by-policy_number read + poll the PAS:** needs an Underwriting code change
  + couples Billing synchronously to two services; the pull path doesn't exist today.
- **Rejected (for MVP) — hybrid JS + reconcile backstop:** most robust but overkill; revisit only if the
  best-effort-publish gap (UW logs + `event_published=False` on a NATS blip) proves to bite.

## Consequences
- **New shared infra:** the `UNDERWRITING_EVENTS` JetStream stream (gitops). Billing egress opens to
  **NATS** (`messaging` ns) — the PAS egress is **not** needed for the MVP (revisit for holder enrichment).
- **Residual gap (accepted, documented):** UW's publish is best-effort; a NATS outage *at publish time*
  can still drop one event (UW flags `event_published=False` + audits it). JetStream covers the
  Billing-down case; the publish-side gap is a later backstop (a reconcile endpoint on UW) if needed.
- **Env:** prod is the authoritative consumer of the prod events; dev/prod durable names are
  env-scoped so the two don't compete (the HR_LIFECYCLE single-durable discipline).
- Ingest DTO is **contract-pinned** by an L3 test against the exact UW payload (field names, the
  `YYYY-MM-DD` date strings, `premium_minor` as an int) — the mock-discipline guard.
