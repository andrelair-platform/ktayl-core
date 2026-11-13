# ADR-009 — Strangler migration and rollback policy

**Status:** Proposed (2026-11-13) · **Owner:** SA/TL · Follows ADR-004/008. Governs roadmap Phase 5.

## Context
When Phase-4 evidence justifies folding an existing service (e.g. Claims) into `ktayl-core`, a big-bang
rewrite of a live, prod system is unacceptable risk. Migration must be incremental, reversible, and
reconcilable — the strangler-fig pattern — with the old system authoritative until the new one is proven.

## Decision
Migrate **one capability at a time**, never the whole service at once:
1. **Explicit routing / façade.** Front the capability with a routing layer (API gateway or an ACL in
   `ktayl-core`) so traffic can be shifted capability-by-capability; the old service stays the fallback.
2. **Old system remains source of truth** until the new module passes acceptance — run **dual-write or
   shadow** where feasible and **reconcile** (compare old vs new outputs on real traffic) before cutover.
3. **Data-ownership transfer is explicit** (ADR-006): the new module's schema is populated + kept in sync
   (backfill + CDC/events) before it owns writes; cut reads over first, then writes.
4. **Compatibility tests + reconciliation reports** gate each step; a mismatch halts the migration.
5. **A tested rollback exists before each cutover** — revert routing to the old service; because the old
   system stayed authoritative/synced, rollback is a routing flip, not a data recovery. No capability
   proceeds without its rollback rehearsed.
6. **Idempotency required** on all migrated money/integration operations (ADR-010) — retries and dual-path
   execution must not double-charge or double-emit.

## Consequences
- **Positive:** each step is small, observable, and reversible; prod risk is bounded to one capability at
  a time; rollback is cheap because the old system is never prematurely demolished.
- **Negative / accepted:** slower + more plumbing (routing, dual-write, reconciliation) than a rewrite —
  the price of not risking a live insurance system.
- **Hard gate:** the old service/repo/DB is **retired only in Phase 6**, after a soak window with the new
  path authoritative and backups/restore verified — never at cutover.

## Follow-ups
ADR-010 (idempotency); the Phase-4 Claims assessment decides *whether* any of this is invoked.
