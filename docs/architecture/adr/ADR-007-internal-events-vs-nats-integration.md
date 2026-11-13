# ADR-007 — Internal domain events vs NATS integration events (+ transactional outbox)

**Status:** Accepted (2026-11-13 — ratified) · **Owner:** SA/TL · Follows ADR-005/006.

## Context
The platform standard async bus is **NATS/JetStream**. The trap when consolidating into a modular
monolith is to put NATS **between every module** — which rebuilds the distributed monolith *inside* the
process (network semantics, serialization, eventual consistency, retries) for interactions that are a
simple in-process method call. We need a clear rule for which interactions are in-process and which cross
a real boundary.

## Decision
1. **Module → module, inside `ktayl-core`: use in-process Spring application events** (or a direct call to
   the published `api/`). Do **NOT** route these through NATS. Example: `ClaimSettled` → Finance updates
   the payment obligation, in-process, in the same deployment.
2. **Crossing a deployment/external boundary: use NATS/JetStream** with a **versioned integration event**.
   Targets: AI services, Reporting/Data-Platform, the legacy-CDC bridge, external partners, any satellite
   service. Internal events are an implementation detail; integration events are a published contract.
3. **Reliable externalization = transactional outbox.** When an internal domain event must also become a
   NATS integration event, publish via an **outbox** (write the outbox row in the same DB transaction as
   the state change; a relay publishes to NATS). Spring Modulith's **event publication registry** tracks
   incomplete publications and supports recovery — use it; validate the exact NATS externalization wiring
   in Phase 2 before relying on it.
4. **Integration events are versioned and backward-compatible;** consumers tolerate unknown fields.

## Consequences
- **Positive:** no accidental distributed monolith inside the app; internal refactors don't break external
  contracts; exactly-once-ish externalization via outbox + idempotent consumers (ADR-010).
- **Negative / accepted:** two event mechanisms to understand (in-process vs NATS) and an outbox+relay to
  operate — justified, because collapsing them is exactly the mistake this ADR prevents.
- **Rule of thumb:** *if it doesn't leave the deployment, it doesn't touch NATS.*

## Follow-ups
ADR-010 (idempotency for consumers + money/integration ops).
