# ADR-014 — Fold `ktayl-claims` and `ktayl-policy-service` into `ktayl-core` (evidence-gated, phased)

**Status:** Proposed (2026-11-14 — awaiting owner ratification) · **Owner:** SA/TL · **Context:** applies the
ADR-004 evidence gate to two specific live services, at owner direction. Builds on ADR-004 (migration is
evidence-gated), ADR-008 (extraction criteria), ADR-009 (strangler migration + rollback), ADR-001 (stack),
and the org rule `architecture-strategy.md`. **Nothing destructive happens on this ADR being written — it
is the decision record; execution begins only after ratification and per-phase gates.**

## Context
The owner has directed that **`ktayl-iam` and `ktayl-underwriting` stay independent** (both have a clear,
still-valid extraction justification — company-wide identity consumed by all; Python-native pricing math),
and that **`ktayl-claims` and `ktayl-policy-service` be folded into `ktayl-core`** as modules. Per ADR-004,
a live service is migrated only on evidence of net value, decided per service — so this ADR records that
evidence test for the two named services rather than executing a merge on assertion.

Authoritative current state (ADR-004 context): **Policy = Go/gRPC, Claims = Java, Underwriting = Python,
IAM = NestJS.** Both target services are **live in prod**. The non-destruction freeze (ADR-004) holds:
**no scaffold/service is deleted until its replacement module ships and a Phase-6 acceptance gate passes.**

## The per-service evidence test (ADR-008 criteria, re-applied in reverse)
"Does the reason it was extracted still hold?" — a *fold* is justified when the extraction reasons have
lapsed and folding produces net value.

### `ktayl-policy-service` (Go/gRPC core PAS)
| ADR-008 extraction reason | Still true? | Read |
|---|---|---|
| Different tech stack | Go — but it is a **thin policy registry** (bound = status `active`, **no premium**); a CRUD registry does not *need* Go's throughput. | Weak — the stack choice is no longer load-bearing |
| Strong data isolation | **No** — policy is the **shared kernel** (party/policy/coverage) that underwriting, claims and billing all read. Low isolation. | **Argues FOR folding** — the shared core belongs in the monolith |
| Consumed by several apps | Yes (UW at bind, claims, billing). | Double-edged: an in-process module call removes the billing↔policy HTTP seam, but **underwriting stays a separate service and still calls it over HTTP** → that seam is *moved*, not removed |
| Independent deploy / team / scaling / SLA | No (solo, same cadence, read-heavy but not extreme). | Weak |

**Net:** policy-service is the **stronger fold candidate on architecture** (shared-kernel data the monolith
wants in-process) but the **highest-risk on execution**: it is the system-of-record for policies (a data
migration of the core) **and** a **Go→Java rewrite**. The UW→policy synchronous coupling is *not* resolved
by the fold while UW stays separate.

### `ktayl-claims` (Java — ACL/Strangler over the frozen `globalcore` legacy)
| ADR-008 extraction reason | Still true? | Read |
|---|---|---|
| Different tech stack | **No** — claims is **already Java**. Folding is a **same-language module absorption**, not a rewrite. | **Cheapest fold technically** |
| Different lifecycle | **Yes** — claims is actively **stranglering the `globalcore` legacy** via Debezium CDC → NATS (`CLAIMS_CDC`). It is mid-migration. | **Argues AGAINST folding now** |
| Strong data isolation | Partial — claims owns its lifecycle but reads policy/party. | Neutral |
| Showcase value | The **Strangler + ACL over a legacy core** is deliberate portfolio/BC03 evidence. | Folding mid-strangler risks the pattern's integrity |

**Net:** claims is the **cheapest technical fold** (Java→Java, no language rewrite) but the **wrong time**:
it is mid-strangler with a live CDC pipeline. Folding should wait until the legacy strangler reaches a
stable boundary, or be done as the *final* strangler step (legacy → claims module in `ktayl-core`).

## Options
- **(a) Keep both extracted** — status quo. Lowest risk; but leaves the shared-kernel policy data outside
  the monolith (the billing↔policy seam stays HTTP) and the owner's consolidation intent unmet.
- **(b) Fold both now** — rewrite policy Go→Java + absorb claims, retire both services. Meets the intent
  fastest; **highest risk** (rewrite + two core data migrations + disrupting an in-flight strangler), and
  destructive to two live prod services — contradicts ADR-004's gate if done without phased evidence.
- **(c) Phased, evidence-gated strangler per service (RECOMMENDED)** — fold each via ADR-009's
  strangler+rollback, policy **first** (it's the shared kernel; higher value, bounded by its thinness),
  claims **after** its legacy strangler stabilises. Each phase is non-destructive until its gate passes.

## Decision (proposed — owner ratifies)
Adopt **(c)**. Specifically:
1. **Sequence: policy-service first, claims later.** Policy is the shared-kernel data the monolith most
   wants in-process and is *thin* (bounded rewrite); claims is cheap to fold technically but must not be
   disturbed mid-strangler.
2. **Per ADR-009 strangler + rollback**, not a big-bang rewrite:
   - **Policy:** stand up a `policy` module in `ktayl-core` (schema-per-module, ADR-006) → dual-write /
     backfill the policy SoR → repoint in-monolith readers (billing) to the module interface → repoint
     `ktayl-underwriting`'s bind calls from the Go gRPC endpoint to `ktayl-core` → soak → **Phase-6 gate** →
     only then retire the Go service. Underwriting stays a separate service; its call target changes, the
     coupling shape does not (accepted — resolving that is a separate concern).
   - **Claims:** defer until the `globalcore` strangler reaches a stable boundary; then absorb the Java
     claims codebase as a `claims` module (same-language) and point the CDC bridge at `ktayl-core`.
3. **Keep `ktayl-iam` and `ktayl-underwriting` independent** (owner decision; extraction still justified).
4. **Non-destruction holds:** no service deleted until its module ships *and* a Phase-6 acceptance gate
   passes (ADR-004). Each phase is independently revertible (ADR-009 rollback).

## Consequences
- **Positive:** the shared-kernel policy data moves in-process (removes the billing↔policy HTTP seam,
  enables transactional invariants across billing+policy); fewer services for a solo operator to run;
  claims absorbed cheaply once its strangler is done.
- **Negative / accepted:** a Go→Java rewrite of the PAS (effort + risk on the core SoR); temporary
  dual-running during each strangler; the UW→policy synchronous call remains (moved, not removed); the
  polyglot (Go) and one Strangler showcase narrow — a deliberate trade for consolidation, recorded here.
- **Risk controls:** phased gates, dual-write + backfill parity checks, ADR-009 rollback per phase, the
  live QA gate before each prod cutover, and the non-destruction freeze.

## Open questions (resolve during Phase planning, not now)
- Does the UW→policy coupling justify also folding `ktayl-underwriting` later, or making the bind path
  async? (Out of scope here — owner keeps UW independent.)
- Exact Phase breakdown + gates per service → the `modernization-roadmap.md` phase table.
