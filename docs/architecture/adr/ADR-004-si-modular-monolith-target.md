# ADR-004 — `ktayl-core` modular monolith is the SI target; existing-service migration is evidence-gated

**Status:** Proposed (2026-11-13) · **Owner:** SA/TL · **Context:** the ktayl insurance IS consolidation
(see [modernization-roadmap.md](../../modernization-roadmap.md)). Builds on ADR-001 (stack) and the org
rule `architecture-strategy.md` (*modular-monolith-first*).

## Context
The core insurance value chain was built microservices-first as independent polyglot services (Policy = Go,
Underwriting = Python, Claims = Java, IAM = NestJS). These domains are tightly coupled by one business
process (quote → bind → policy → claim → settle) and share core entities (party, policy, coverage), under
a single owner with no independent-team or independent-scaling pressure on the transactional path. The
result is the cost of distribution (network on the bind path, polyglot tax, a distributed monolith) without
its benefit. We need a durable target architecture **and** a disciplined rule for what to do with the
services that already exist and work in production.

## Decision
1. **`ktayl-core` (Java 21 + Spring Modulith) is the DEFAULT home for insurance business capabilities.**
   Every *new* custom business domain is a **module** inside `ktayl-core` — not a new repo/service/board/
   pipeline. One deployable app, N replicas, one CNPG cluster, schema-per-module.
2. **Existing services are NOT migrated by default.** Migration of a live service into `ktayl-core` is
   **conditional on evidence** that it produces net value (cost/risk vs benefit), decided per service at
   its roadmap gate. A healthy service with a reliable pipeline is **kept** until evidence says otherwise.
3. **Some distribution is permanent and correct.** Identity (`ktayl-iam`), AI/GPU services, the legacy-CDC
   bridge, and Retrieva (a separate *product*) stay separate by design — see ADR-008 for the test.
4. **The objective is not "fewest services."** It is clear business ownership, controlled dependencies,
   reliable transactions, independent evolution where genuinely needed, and manageable operations.

## Why (vs the alternatives)
- **vs "everything into `ktayl-core`" (the over-correction):** too absolute. Rewriting a working, live Go
  PAS or Python pricing engine into Java for tidiness destroys value and risks prod for no business gain.
  The evidence gate prevents that.
- **vs "keep microservices-first":** rebuilds the distributed-monolith cost for every *new* domain too —
  the mistake we're correcting. New work must default to modules.
- **vs a non-enforced monolith:** boundaries rot into a big ball of mud. Spring Modulith verifies the
  *code* boundaries in CI (cycles, no internal-package access) — necessary but **not sufficient**; data +
  invariant enforcement are separate concerns (ADR-006). We adopt all three layers.

## Consequences
- **Positive:** new domains ship as modules (cheap, coherent, in-process); the extraction escape-route
  stays open and honest; we stop multiplying the per-service tax; existing working systems are protected
  from gratuitous rewrites.
- **Negative / accepted:** `ktayl-core` becomes a larger codebase (managed by module boundaries + verify
  tests); one CNPG is a shared failure domain (mitigated by PITR + tested restore, ADR-006); a mixed
  reality persists for a while (modules + a few legacy polyglot services) — acceptable and documented.
- **Honest framing (portfolio):** the existing polyglot services are **kept, justified, demonstrable
  proof** of distributed-systems capability — not a repudiated mistake. The lesson is applied *forward*.

## Scope guard
This ADR authorises **new-build as modules** and **analysis** of existing services. It does **not**
authorise deleting any repo/service/DB or consolidating prod — that is roadmap Phase 6, after acceptance.

## Follow-ups
ADR-005 (bounded contexts/ownership) · ADR-006 (schema/transactions) · ADR-007 (events) ·
ADR-008 (extraction criteria) · ADR-009 (strangler/rollback) · ADR-010 (jobs/idempotency).
