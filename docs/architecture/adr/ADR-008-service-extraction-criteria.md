# ADR-008 — When a module earns extraction to a separate service

**Status:** Proposed (2026-11-13) · **Owner:** SA/TL · Follows ADR-004.

## Context
ADR-004 makes modules the default. We need an explicit, evidence-based test for the exception — when a
module (or an existing service) genuinely warrants being its own deployment — so "microservice" is a
decision with named forces, not a reflex or a tidiness impulse in either direction.

## Decision
Extract/keep-separate **only** when **one or more real forces** hold (size alone is never one):

| Force (evidence required) | Example in ktayl |
|---|---|
| **Independent scaling** — a workload needs resources on a different curve | AI/GPU document processing; heavy analytics |
| **Different stack is intrinsic** — the problem is native to another ecosystem | Underwriting **pricing/actuarial math** (Python) |
| **Different lifecycle / external system** — wrapping legacy or a partner | the **`globalcore` legacy-CDC bridge** |
| **Consumed by many apps as a platform capability** | **`ktayl-iam`** (identity for every app) |
| **Independent team ownership + release cadence** | not applicable today (solo) — would require a real team |
| **Regulatory / data isolation mandates separate infra** | evaluate per regulation; rare |
| **It is a separate product, not an IS domain** | **Retrieva** (the cert deliverable) |

Rules:
- **Size is not a force — at either level.** A context growing to hundreds of sub-capabilities (the
  two-level model, ADR-005) does **not** justify splitting the context or extracting a service; it grows
  internally. 100k lines of Claims code does **not** justify a Claims service; a Claims workload that needs
  independent scaling/ownership might.
- **"Already works + reliable pipeline"** is a reason to **keep as-is**, not to migrate — migrating a
  healthy service for architectural tidiness is net-negative (ADR-004).
- **Decision is recorded** (a one-line note per domain in the inventory, or an ADR for a boundary change)
  so "why is this separate?" is auditable.
- **Default on a tie:** module. The burden of proof is on *extraction*, not on *keeping it in-core*.

## Consequences
- **Positive:** a defensible, repeatable call; no fashion-driven services; no gratuitous rewrites.
- **Negative / accepted:** some judgement required per case (evidence isn't always clean) — mitigated by
  the "default to module, burden on extraction" rule.

## Follow-ups
Applied at roadmap Phase 4 (Claims assessment) and whenever a new domain is classified (Phase 1).
