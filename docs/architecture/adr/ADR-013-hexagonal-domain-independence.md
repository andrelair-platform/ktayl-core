# ADR-013 — Hexagonal architecture within modules: the business logic survives the tech

**Status:** Accepted (2026-11-13) · **Owner:** SA/TL · refines ADR-005 (module ownership layout). Applies
to **new** business modules; billing is pre-hexagonal (align later, not blocking).

## Context
A regulated insurer's business rules (how a claim is adjudicated, how a premium is rated, how a complaint
must be acknowledged within a statutory window) outlive any framework choice — they are maintained for
5–10 years while Spring, JPA, Postgres and REST all change underneath. If the domain logic is **coupled to
the framework** (e.g. the aggregate *is* a `@Entity`, business rules live in a `@Service` next to JPA calls),
then a tech change rewrites business code, and the rules are hard to test without a database/Spring context.
The modular monolith (ADR-004/005) gives us module boundaries; this ADR gives us the boundary *inside* a
module so the business logic is **independent of the tech**.

## Decision
Each business module follows **hexagonal architecture (ports & adapters / Clean Architecture)**:
dependencies point **inward only** — `infrastructure → application → domain`, and **the domain depends on
nothing outward.**

```
com.ktayl.core.<module>/
├── domain/          # PURE — plain Java. The aggregate, value objects, domain events, invariants,
│                    #   and the OUTBOUND PORTS (repository/gateway interfaces). ZERO imports of
│                    #   org.springframework.* or jakarta.persistence.* or any web/infra type.
├── application/     # use cases — orchestrate the domain + the ports. Thin. (Spring @Service/@Transactional
│                    #   live here, wiring, not business rules.) Depends only on domain.
├── infrastructure/  # ADAPTERS — the ONLY place frameworks live:
│   ├── persistence/ #   JPA entity + Spring Data repo + an adapter that IMPLEMENTS the domain's port
│   │                #   (maps entity ↔ domain object). The domain never sees JPA.
│   ├── web/         #   REST controller (inbound adapter) → calls the application use case
│   └── messaging/   #   event externalisation / NATS (when it leaves the deployment — ADR-007)
└── api/ , events/   # what OTHER modules consume (published interface + domain event) — ADR-005 named interfaces
```

**Rules:**
1. **The domain is pure.** No `org.springframework.*`, no `jakarta.persistence.*`, no web/HTTP/NATS types in
   `domain/`. The aggregate is plain Java; business rules are methods on it; invariants throw domain
   exceptions. It can be fully unit-tested **with no Spring context and no database**.
2. **Ports are owned by the domain/application, implemented by infrastructure** (dependency inversion): the
   repository is an **interface** in `domain/`; the JPA adapter in `infrastructure/persistence/` implements it.
   The use case depends on the interface, never on JPA.
3. **Dependency direction is inward only.** `infrastructure` may import `application`+`domain`; `application`
   may import `domain`; `domain` imports **nothing** of ours outward. Enforced by an ArchUnit test (below).
4. **Entities ≠ aggregates.** The JPA `@Entity` is an infrastructure persistence model; the domain aggregate
   is separate and pure; the adapter maps between them. (This is the key difference from billing.)

## Enforcement (tooling, not review)
A `HexagonalArchitectureTests` ArchUnit test (alongside `ModularityTests`) **fails the build** if:
- any type in a `..domain..` package imports `org.springframework..` or `jakarta.persistence..`;
- `..domain..` depends on `..infrastructure..` (wrong-direction dependency).
So domain purity is guaranteed by CI, the same way module boundaries are (ADR-005).

## Why (vs the alternatives)
- **vs the anemic-entity + service style (billing today):** fast to write, but the aggregate *is* a JPA
  entity → business rules are coupled to persistence, hard to test without a DB, and a persistence/ORM change
  edits business code. Fine for billing's money-plumbing MVP; not the standard for new domain-rich modules.
- **vs full onion with a mapping explosion:** we keep it proportionate — one adapter + one mapper per
  aggregate, not a DTO at every layer. The purity rule + the port is the 20% that buys the 80% (testable,
  tech-independent domain). Don't gold-plate.

## Consequences
- **Positive:** business rules are plain Java — unit-tested with no Spring/DB (fast, exhaustive); the tech
  (JPA, Postgres, Spring, REST, NATS) is swappable without touching the domain; the domain reads as the
  ubiquitous language, not JPA boilerplate. Strong interview/portfolio signal (hexagonal in a real domain).
- **Negative / accepted:** an explicit entity↔domain mapper per aggregate (a little boilerplate) and the
  discipline of the dependency direction — cheap, and the ArchUnit guard keeps it honest.
- **Scope:** new business modules (complaints first — CMPL-011 is the exemplar). Billing is a documented
  pre-hexagonal exception; align it opportunistically, not as a blocking migration.

## Reference implementation
`com.ktayl.core.complaints` (CMPL-011): pure `domain.Complaint` + `domain.ComplaintRepository` port +
`infrastructure.persistence` JPA adapter + `application.RegisterComplaint` use case. See
`docs/specs/spec-complaints/SPEC.md`.
