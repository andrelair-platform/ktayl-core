# AGENTS.md — ktayl-core (tiny verified context block)

Only what the code can't cheaply say (policy · command-catches · non-default conventions · observed
pitfalls). NOT a repo overview — read the code for structure. Keep this small.

## Policy
- **Modular monolith, enforced.** New insurance business domains are **modules** here
  (`com.ktayl.core.<domain>`), **never** a new repo/service — ADR-004 + `docs/modernization-roadmap.md`.
  Every module is a **CLOSED** `@ApplicationModule`: others use only its `api/` + `events/`, never
  `internal/`. Boundaries are enforced in CI by `ModularityTests` (`ApplicationModules.verify()`) — don't
  weaken it.
- **Schema-per-module.** Each module owns its Postgres schema + Flyway migrations and reads **no other
  module's schema** (ADR-006). Reference other domains by **identifier**, via their published `api/`.
- **Events:** cross-module = **in-process Spring application events** (ADR-007). **Do NOT add NATS between
  modules** — NATS is only for leaving the deployment (external systems).
- **Money = integer minor units** (eurocents), never floating point; reconcile breakdowns to totals.
- **Decisions live in `docs/architecture/adr/`** (ADR-001..011) + `docs/modernization-roadmap.md` — read the
  relevant ADR before a boundary/schema/event/extraction change.

## Command catches
- **Build + the boundary guard:** `mvn -B -ntp verify` (runs `ModularityTests`; no wrapper — use `mvn`).
  Java 21.
- **Prove the guard bites:** temporarily reference another CLOSED module's `internal` type → `verify()`
  fails. Never commit that.

## Conventions that differ from defaults
- Module package layout: `<module>/{api, events, internal/{domain, application, persistence, web}}` +
  a `package-info.java` carrying `@ApplicationModule(displayName = "…")`. Reference: `com.ktayl.core.billing`.
- Flyway migrations: `src/main/resources/db/migration/V<n>__<desc>.sql`, **one schema per module** (next
  free = `V4`). A migration creates/owns only its module's schema.
- `/api/**` is SSO-gated (Authentik OIDC resource-server, `shared/config`); the **actor is derived from the
  token**, never a placeholder (audit attribution).

## Observed pitfalls
- Kargo **git-Warehouse** (not image): the JVM base-layer date is fixed, which breaks `NewestBuild`
  ranking → promotion stalls silently. Stays on the git model (gitops `services/ktayl-core/kargo/`).
- A new module is auto-discovered by `ModularityTests` — no test wiring needed; but a **new CLOSED module
  with a cross-module internal import compiles fine yet fails `verify()`** (that's the guard working).

Pointers: `docs/architecture/solution-architecture.md` (SDD) · `docs/specs/*/SPEC.md` (per-epic contracts).
