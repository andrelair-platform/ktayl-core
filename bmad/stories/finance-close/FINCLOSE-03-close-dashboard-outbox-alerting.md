---
id: FINCLOSE-03
title: "close dashboard read API + GL outbox-drift metrics & alerting"
status: Ready
type: Story
epic: finance-close
milestone: "FINCLOSE — Finance Close & Reconciliation v1"
estimate: 5
labels: [java, backend, finance, observability, ktayl-core]
priority: P3
assignee: AndreLiar
repo: andrelair-platform/ktayl-core
project: 28
initiative: Insurance LOB
---

## Story

As a **finance officer / SRE**, I want a **multi-period close dashboard** and **alerting on GL outbox drift**
(pending/failed postings past an SLA), so that the state of the close across recent periods is visible at a
glance and a stuck GL posting pages someone before it becomes a month-end surprise.

## Background

FINCLOSE-01 gave per-period **reconciliation** + **register**; FINCLOSE-02 added **close/reopen** + the
**immutable audit**. What's missing is the *cross-period* view and the *proactive* signal: today you must query
one period at a time, and a `FAILED` GL posting only surfaces if someone looks. ktayl-core already exports
Micrometer → Prometheus (`/actuator/prometheus`, ADR-012) and the platform has Prometheus + Alertmanager +
Grafana — so this is wiring the finance-close signals onto that existing triad, not new infra.

## Scope

**In:** GL-outbox health **metrics** (billing-owned gauges), a **Prometheus alert** on outbox drift, a finance
**close-dashboard read endpoint** (recent periods × status/drift/stuck), and a **Grafana dashboard**.
**Out:** a bespoke UI (the Grafana dashboard is the surface); email/paper reporting; anything needing ERPNext
to be live (GL side stays `UNAVAILABLE` on dev).

## Acceptance Criteria

- [ ] **AC-1 — GL outbox metrics (billing-owned).** Billing registers Micrometer gauges over `ledger_outbox`
      (it owns the table): `ledger_outbox_pending` (count), `ledger_outbox_failed` (count, `attempts > 0`),
      `ledger_outbox_oldest_pending_age_seconds` (now − oldest pending `created_at`; 0 when none). Tagged so
      they're usable in alerts. Exposed on `/actuator/prometheus`. These are **billing-internal** gauges — no
      cross-module type needed (metrics are a billing concern; finance consumes them via Prometheus, not a
      port).
- [ ] **AC-2 — outbox-drift alert (gitops).** A Prometheus alert rule (in `minicloud-gitops`, the platform's
      alert home) fires `FinanceGLOutboxDrift` when `ledger_outbox_failed > 0 for 15m` OR
      `ledger_outbox_oldest_pending_age_seconds > <SLA, e.g. 3600> for 15m` → routed to the finance/SRE
      Alertmanager receiver. Severity `warning`; a runbook link in the annotation.
- [ ] **AC-3 — close-dashboard read endpoint.** `GET /api/finance/dashboard?from=YYYY-MM&to=YYYY-MM` (default:
      last 6 periods ending this month) returns, per period: `period`, `periodStatus` (OPEN/CLOSED),
      `subledgerPostedMinor`, `glStatus`, `driftMinor`, `pendingCount`, `failedCount`, `lastClosedAt`,
      `postCloseMovement` — i.e. the FINCLOSE-01/02 figures for a *range*, in one call. `GROUP_Finance`-gated;
      a malformed/over-wide range → 400 (cap the span, e.g. ≤ 24 periods).
- [ ] **AC-4 — reuse, don't duplicate.** The dashboard endpoint is a thin fan-out over the existing
      `FinanceCloseOperations.reconcile(period)` across the range (bounded) — no new reconciliation logic, no
      second data path. Money stays `long` minor units.
- [ ] **AC-5 — Grafana dashboard (gitops).** A provisioned Grafana dashboard JSON (in `minicloud-gitops`)
      with: outbox pending/failed/oldest-age over time, and a per-period close table (drift, stuck, status) —
      sourced from the metrics (+ optionally the dashboard endpoint). Appears under the finance/IS folder.
- [ ] **AC-6 — authority + errors.** Dashboard endpoint 401 (no token) / 403 (not Finance) / 400 (bad range),
      consistent with FINCLOSE-01/02; never a 5xx on bad input.

## Technical Notes

- **Metrics placement:** register the gauges in `billing` (e.g. a `@Component` in `billing.ledger` using
      `MeterRegistry` with suppliers that query `LedgerOutboxRepository` counts/oldest). Gauges are polled by
      Micrometer — keep the queries cheap (count + a `min(created_at) where status='pending'`). Add the two
      repo methods (`countByStatus`, oldest-pending timestamp) if not present.
- **Dashboard endpoint:** add `dashboard(YearMonth from, YearMonth to)` to `FinanceCloseOperations` (or a
      small `FinanceDashboard` api port) returning a `List<PeriodReconciliation>` (reuse the existing DTO) —
      the service loops the range calling `reconcile`. Bound the range in the controller (400 if > cap) so a
      huge range can't hammer the DB.
- **Alert + dashboard live in `minicloud-gitops`** (the deployment repo), not ktayl-core — Prometheus rules
      under the monitoring/alerts path, Grafana dashboard under the Grafana provisioning path. CODEOWNERS/
      prod rules apply there as normal. Mirror an existing service's alert rule + dashboard for the pattern.
- **No ERPNext dependency:** on dev the GL is `UNAVAILABLE`, so the dashboard shows subledger + stuck counts
      and `glStatus=UNAVAILABLE` — the outbox-drift alert (pending/failed/age) is **independent of the GL**
      and is the primary signal.

## Definition of Done

- [ ] Code implements AC-1..AC-6.
- [ ] **L0** `mvn -ntp verify` clean.
- [ ] **L1** unit — the oldest-pending-age + counts logic (pure/над a stubbed repo) and the dashboard range
      fan-out/cap; ≥70% on new code.
- [ ] **L2** integration (real Postgres) — seed outbox rows across statuses/periods; assert the gauges read
      correct values and the dashboard endpoint returns the right per-period rows.
- [ ] **L3** web — dashboard endpoint 200 (range) / 400 (bad/over-wide) / 401 / 403; the `/actuator/prometheus`
      scrape exposes the three gauges.
- [ ] `ModularityTests` + `HexagonalArchitectureTests` green (metrics stay billing-internal; no new cross-module leak).
- [ ] gitops: the `FinanceGLOutboxDrift` alert rule + the Grafana dashboard JSON merged (their own PR; prod-gated there).
- [ ] OpenAPI updated (`api/finance.openapi.yaml`) for `/api/finance/dashboard`.
- [ ] Live check on dev: `/actuator/prometheus` shows the gauges; the dashboard endpoint returns the last-6-periods summary (401-gated).

## Tasks

- [ ] TASK-1: billing — `MeterRegistry` gauges over `ledger_outbox` (pending/failed/oldest-age) + repo methods.
- [ ] TASK-2: finance — `dashboard(from,to)` api + service fan-out over `reconcile` + range cap.
- [ ] TASK-3: web — `GET /api/finance/dashboard` + range validation + exception mapping; OpenAPI.
- [ ] TASK-4: gitops — `FinanceGLOutboxDrift` Prometheus alert rule + Alertmanager routing.
- [ ] TASK-5: gitops — provisioned Grafana dashboard JSON (outbox health + per-period close table).
- [ ] TASK-6: L1/L2/L3 tests.

## Dependencies

- Depends on: **FINCLOSE-01 + 02** (merged, live) — reuses `reconcile` + the outbox.
- Blocks: nothing (this is the observability cap on the finance-close epic).
