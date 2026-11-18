---
id: FINCLOSE-00
title: "EPIC: Finance close & GL reconciliation (finance module)"
status: Ready
type: Epic
epic: finance-close
milestone: "FINCLOSE — Finance Close & Reconciliation v1"
estimate: 8
labels: [epic, insurance-lob, finance, ktayl-core]
priority: P2
assignee: AndreLiar
repo: andrelair-platform/ktayl-core
project: 28
initiative: Insurance LOB
---

## Epic

Build a `finance` module in the ktayl-core modular monolith that closes the money loop with an **auditable
period close**: reconcile what `billing` posted to the general ledger, produce a per-period **premium
register / trial balance**, and let a finance officer **close a period** (lock it, with a closing audit
record). This is the ktayl-core-module realisation of `ktayl-finance` **FIN-05** (board #14).

## Why

`billing` already posts per-transaction Journal Entries to ERPNext (outbox pattern, migration V8 — see the
sprint overview). What the IS still lacks is the **period-end** view every regulated insurer and every
auditor expects:

- *"Does the GL agree with the billing subledger for this month?"* — **reconciliation**.
- *"Show me the premium register / trial balance for October."* — the **close report**.
- *"This period is closed — no more postings land in it."* — the **period lock + audit**.

It completes the `submission → underwriting → policy → billing → GL` flow with a demoable, auditable close,
reuses billing's exact patterns (schema-per-module Flyway, `api/` `@NamedInterface`, CNPG datastore,
git-Warehouse deploy), and needs **no new vendor and no actuarial modelling**.

**Certification value:** the reconciliation + closing audit are concrete BC02 (concevoir) / BC03 (déployer &
sécuriser) evidence — a traceable financial control with a tamper-evident audit trail.

## Scope (epic-level)

- [ ] **FINCLOSE-01** — `finance` module scaffold + GL **reconciliation report** (billing subledger ↔ ERPNext)
      + **premium register** for a period. (This sprint.)
- [ ] **FINCLOSE-02** — **accounting-period lifecycle**: open/closed periods, **close a period** (lock) with a
      closing audit record; block/park postings that target a closed period.
- [ ] **FINCLOSE-03** — close **dashboard read API** + **outbox-drift alerting** (pending/failed GL rows past
      SLA surfaced as a metric / finance alert).

## Out of scope (deferred, stays on ktayl-finance #14)

- FIN-02 **IFRS 17** measurement (CSM, risk adjustment) — large; likely adopt, not build.
- FIN-03 **actuarial reserving** (case + IBNR → GL provisions) — needs claims reserve data + an actuarial model.
- FIN-04 **reinsurance bordereaux / cession reporting** — waits on the reinsurance module.

## Architecture constraints (binding on every story)

- **Modulith boundary:** `finance` consumes `billing`'s `api/` `@NamedInterface` only — never billing tables
  or internal types. The existing `ModularityTests` must stay green.
- **Schema-per-module:** finance owns its own Flyway migrations, starting at **V14** (V13 is the current head).
- **Money is minor units (eurocents), `long`** — never a float (matches `JournalEntry`).
- **ERPNext is the GL system of record** — finance *reconciles against* and *reads* ERPNext; it does not
  become a second ledger.
- **Authority:** finance endpoints gated by a `GROUP_Finance` authority (Authentik group), mirroring the
  per-module group→authority pattern (`shared/config/SecurityConfig`).

## Definition of Done (epic)

- [ ] FINCLOSE-01 merged + live on dev; reconciliation report + premium register reachable and correct.
- [ ] `finance` module passes `ModularityTests` (clean `api/` boundary) and the Tier-A test layers it needs.
- [ ] A worked example in the ktayl-core docs: a period reconciled + its premium register, with the
      ERPNext-vs-subledger figures agreeing (or the drift explained).
- [ ] FINCLOSE-02 / FINCLOSE-03 decomposed into ready stories (`/bmad-create-epics-and-stories`).

## Dependencies

- Depends on: `billing` module (LIVE), ERPNext GL (adopted, board #8).
- Blocks: a complete finance-close story for the IS; later reinsurance/IFRS layers build on the period model.
