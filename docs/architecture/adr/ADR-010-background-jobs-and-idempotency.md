# ADR-010 — Background-job execution under N replicas, and idempotency

**Status:** Accepted (2026-11-13 — ratified) · **Owner:** SA/TL.

## Context
`ktayl-core` runs **2–3 replicas** for availability. A naïve Spring `@Scheduled` method runs **on every
replica** — so a billing run, a notification sweep, or an integration push fires **N times**. In an
insurer that means duplicate premium charges, duplicate broker emails, duplicate reinsurance cessions —
real-money defects. Scheduling and idempotency must be decided *before* scaling replicas, not after an
incident.

## Decision
1. **No unguarded `@Scheduled` on a multi-replica Deployment.** For any scheduled/critical job, choose an
   explicit single-execution model:
   - **Kubernetes `CronJob`** (same image, a `--job=<name>` profile) — preferred for discrete batch work
     (billing run, reconciliation, report generation). One pod, one run.
   - **Dedicated scheduler Deployment** (replicas: 1) for a long-lived scheduler, **or**
   - **Distributed lock** (e.g. ShedLock on the shared Postgres) if a job must live inside the main app —
     only one replica acquires the lock and runs.
2. **Idempotency is mandatory for every money and integration operation.** Use a natural idempotency key
   (invoice id, claim id + operation) + a uniqueness/"already-done" check, so a retry, a dual-path
   (strangler) execution, or a redelivered NATS event cannot double-apply. Consumers of NATS integration
   events (ADR-007) are idempotent by construction.
3. **Workers are the same image, a different entrypoint/profile** — not a separate codebase (12-factor
   admin-process parity).
4. **Observability:** every scheduled run emits a start/stop + outcome metric; a missed or duplicated run
   is alertable.

## Consequences
- **Positive:** safe horizontal scaling; no duplicate billing/notifications/integration calls; retries and
  strangler dual-paths are safe because operations are idempotent.
- **Negative / accepted:** a bit more infrastructure (CronJobs / a lock / a worker profile) and the
  discipline of an idempotency key on every mutating money/integration op — non-negotiable for an insurer.
- **Rule:** before adding a second replica to anything that schedules work, confirm its jobs are
  single-execution **and** idempotent.

## Follow-ups
Wire the first CronJob + ShedLock pattern in roadmap Phase 2 (foundation); enforce idempotency review in
the Definition of Done for any money/integration operation.
