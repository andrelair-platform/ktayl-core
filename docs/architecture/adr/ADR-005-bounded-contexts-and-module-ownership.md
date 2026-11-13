# ADR-005 — Bounded contexts and module ownership in `ktayl-core`

**Status:** Proposed (2026-11-13) · **Owner:** SA/TL · Follows ADR-004.

## Context
A modular monolith only works if modules have **explicit boundaries and own their state**. The failure
mode is modules reaching into each other's tables/internals, which recreates a big ball of mud with none
of microservices' isolation. We need (a) the bounded-context map and (b) a concrete, enforceable
definition of what a module owns and how modules interact.

## Decision

### Bounded contexts (business boundaries drive the modules — not folder count)
~8 contexts, each holding several modules, all inside `ktayl-core`:

| Bounded context | Modules |
|---|---|
| **Party & Distribution** | Customer/MDM (shared kernel), Broker, CRM, binders, commissions |
| **Product & Policy (PAS)** | Products, quotes, policies, renewals, endorsements |
| **Underwriting** | Risk evaluation, rating/pricing, referral, approval |
| **Claims** | FNOL, assessment, settlement, subrogation, recovery |
| **Billing & Finance** | Premium, invoicing, cash allocation, GL-posting, reserving |
| **Reinsurance** | Treaty/fac, cessions, bordereaux, recoveries |
| **Compliance & Audit** | DORA-internal, GDPR, AML, regulatory reporting, audit trail |
| **Documents** | Generation, OCR/IDP, classification, retention |
| (+ Risk-Engineering, International-Programs, Submission, Complaints, Procurement as modules) | |

### What a module OWNS (the enforceable contract)
Standard Spring Modulith package layout per module (reference: Claims):
```
claims/
├── api/            # PUBLISHED: interfaces + DTOs other modules may use (ClaimOperations, ClaimSummary)
├── internal/       # PRIVATE: domain (aggregates/invariants), application, persistence, web
│   └── domain/     #   Claim, ClaimStatus, ClaimSettlement — never referenced from outside
└── events/         # PUBLISHED domain events (ClaimRegistered, ClaimSettled)
```
Rules:
1. A module owns its **state, invariants, and business operations**. Its `internal/` is private.
2. Other modules interact **only** through the module's `api/` (published interface) or its `events/`
   (published domain events) — **never** its repositories, entities, or tables.
3. Example invariant: Finance must **not** mutate a Claim to issue a payment — it calls a published Claims
   operation or reacts to `ClaimSettled`. This yields **logical independence inside a shared deployment**.
4. **Customer/Party is a shared kernel**, not a free-for-all: a small, stable published model (IDs + a
   read interface) that other modules reference by identifier, not by reaching into party tables.

### Enforcement (three layers — Modulith is only the first)
- **Code:** `ApplicationModules.verify()` in CI fails the build on cycles / access to another module's
  `internal/`. (ADR-001 already mandates this test.)
- **Data:** schema ownership + no cross-schema joins — ADR-006.
- **Domain:** aggregate boundaries + a service layer for cross-module operations — this ADR.

## Consequences
- **Positive:** modules evolve independently; the extraction escape-route is cheap (lift a context's
  schema + published API); reviews can reason per-module.
- **Negative / accepted:** more up-front design (the context map + published contracts) and some
  ceremony (api/internal/events split) vs a flat app — the cost of keeping boundaries honest.
- **Note:** Spring Modulith verifies *code* dependencies; it does **not** block a raw SQL cross-schema
  read or guarantee aggregate invariants — those are ADR-006 + the domain layer. Do not over-trust `verify()`.

## Follow-ups
ADR-006 (schema ownership) · ADR-007 (which interactions are in-process events vs NATS).
