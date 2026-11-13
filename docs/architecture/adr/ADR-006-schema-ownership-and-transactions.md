# ADR-006 — PostgreSQL schema ownership and transaction strategy

**Status:** Accepted (2026-11-13 — ratified) · **Owner:** SA/TL · Follows ADR-005.

## Context
`ktayl-core` runs on one CNPG cluster with **schema-per-module**. A schema is *not automatically* a
bounded context, and one shared cluster is a shared operational failure domain. We need explicit data
ownership so modules stay isolated at the data layer (not just the code layer), and a clear transaction
model for cross-module operations.

## Decision
1. **Each module owns its schema, tables and Flyway migrations.** `claims` → schema `claims`, `policy` →
   `policy`, `party` → `party`, etc. No module writes another module's schema.
2. **No cross-module SQL joins or arbitrary cross-schema reads.** Cross-module data is obtained through a
   module's published `api/` (ADR-005), referencing other data **by identifier**, not by joining its tables.
3. **Cross-module foreign keys are restricted by policy:** prefer **IDs + explicit consistency** (lookups
   via the owning module, or domain events) over hard FK constraints across schemas, so a context can be
   extracted later without untangling shared constraints. Hard FKs allowed only *within* a module's schema.
4. **Transactions:** a business operation that spans modules runs through a **service/application layer**
   with an explicit `@Transactional` boundary *within* the owning module; cross-module effects propagate
   via **domain events** (ADR-007), not by one module opening a transaction across another's tables.
5. **The cluster is a shared failure domain — treat it as one:** daily CNPG `ScheduledBackup` + WAL
   **PITR to R2**, connection-pool limits per module/workload, and a **tested restore** (not just a backup).
   Per `datastore-selection.md` + `reliability-and-gamedays.md` — a backup without a proven restore is not DR.

## Consequences
- **Positive:** data-layer isolation matches code-layer isolation; a context is lift-and-shift extractable
  (its schema + published API); blast radius of a bad migration is one schema.
- **Negative / accepted:** lose some convenience of cross-table joins and DB-enforced referential integrity
  across modules (replaced by app-level consistency + events); one cluster = one restore path to keep green.
- **Guard:** resist the temptation to "just join across schemas for a report" — that silently couples
  modules. Reporting reads go through the Data Platform (dbt copy), not live cross-schema joins.

## Follow-ups
ADR-007 (events as the cross-module consistency mechanism); ADR-009 (data-ownership transfer on migration).
