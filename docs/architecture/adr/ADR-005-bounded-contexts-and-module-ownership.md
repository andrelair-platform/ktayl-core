# ADR-005 — Bounded contexts and module ownership in `ktayl-core`

**Status:** Accepted (2026-11-13 — ratified) · **Owner:** SA/TL · Follows ADR-004.

## Context
A modular monolith only works if modules have **explicit boundaries and own their state**. The failure
mode is modules reaching into each other's tables/internals, which recreates a big ball of mud with none
of microservices' isolation. We need (a) the bounded-context map and (b) a concrete, enforceable
definition of what a module owns and how modules interact.

## Decision

### The two-level model — ~8–12 bounded contexts, each with MANY sub-capabilities (how we scale to hundreds)
**An insurer does not have hundreds of independent business domains — it has hundreds of capabilities
belonging to a small number of bounded contexts.** ktayl will have **~8–12 contexts** but, over time,
**dozens-to-hundreds of sub-capabilities** inside them. The number you *govern* is the **context** count;
capabilities multiply *within* a context freely. Business boundaries (contexts) drive architecture — the
count of folders, modules, or Spring `@ApplicationModule`s does not.

Mapping onto Spring Modulith (resolves the naming ambiguity — "module" means two things):
- **Bounded context = a top-level `@ApplicationModule`** (the governed unit, owns a **schema**, has the
  `api/ internal/ events/` contract, is the extraction unit if it ever earns it — ADR-008).
- **Sub-capability = a nested module** *inside* a context (`claims.fnol`, `claims.settlement`, …). They
  share the context's schema + aggregates; they do **not** each get their own schema, published API, or
  extraction story. `ApplicationModules.verify()` governs the **context** boundaries; intra-context
  structure is ordinary package discipline.

So "100 modules, ~10 contexts" is the target shape — and **module count is never a reason to split a
context or extract a service** (ADR-008: size is not a force). A context grows internally; it is split
only if a true sub-boundary emerges (a distinct ubiquitous language + its own invariants), not because it
got large.

| Bounded context (top-level module, owns a schema) | Sub-capabilities (nested modules inside it) |
|---|---|
| **Party & Distribution** | Customer/MDM (shared kernel), Broker, CRM, relationships, binders, commissions |
| **Product & Policy (PAS)** | Products, quotations, policies, renewals, endorsements |
| **Underwriting** | Risk evaluation, rating/pricing, referral, approval |
| **Claims** | FNOL/notification, assessment, settlement, subrogation, recovery |
| **Billing & Finance** | Premium, invoicing, cash allocation, payments, GL-posting, reserving |
| **Reinsurance** | Treaty, facultative, cessions, bordereaux, recoveries |
| **Compliance & Audit** | DORA-internal, GDPR, AML, regulatory reporting, audit trail |
| **Documents** | Upload, generation, OCR/IDP, classification, retention |
| **Risk Engineering** | Site audit, inspection, prevention recommendations, scoring |
| **International Programs** | Master/local policies, PO/SO network, cross-border flows |
| **Submission** | Intake, exposure dossier, triage |
| **Complaints** | Register, statutory deadlines, ombudsman escalation, root-cause |
| **Procurement & CLM** | Business-vendor contracts, lifecycle, obligations *(or adopt)* |

> **Governance rule:** adding a *sub-capability* to an existing context is routine (no ADR). Adding or
> splitting a **bounded context** is an architectural change → a short ADR (it changes a schema boundary +
> a published contract). This keeps the small set of contexts — the thing that must stay coherent — under
> deliberate control while capabilities multiply freely underneath.

### What a context (top-level module) OWNS (the enforceable contract)
Standard Spring Modulith layout — the **published contract is at the CONTEXT level**; sub-capabilities are
ordinary nested packages that share the context's schema + aggregates (reference: Claims):
```
claims/                         # the bounded context (owns schema `claims`)
├── api/            # PUBLISHED: interfaces + DTOs other CONTEXTS may use (ClaimOperations, ClaimSummary)
├── events/         # PUBLISHED domain events (ClaimRegistered, ClaimSettled)
└── internal/       # PRIVATE to the context — no other context may reference these:
    ├── fnol/       #   sub-capability (nested module)
    ├── assessment/ #   sub-capability
    ├── settlement/ #   sub-capability
    └── domain/     #   Claim, ClaimStatus, ClaimSettlement — aggregates/invariants, never referenced outside
```
Rules:
1. A **context** owns its **schema, state, invariants, and business operations**. Its `internal/` (including
   every sub-capability inside it) is private. Sub-capabilities do **not** publish their own cross-context API.
2. Other **contexts** interact **only** through the context's `api/` (published interface) or its `events/`
   (published domain events) — **never** its repositories, entities, or tables. (Within a context,
   sub-capabilities may call each other directly — that's ordinary internal structure.)
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
