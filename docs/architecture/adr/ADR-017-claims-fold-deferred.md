# ADR-017 — Claims fold: DEFER (keep ktayl-claims as the standalone ACL/strangler, with preconditions)

**Status:** Accepted (2026-11-15) · **Owner:** SA/TL · **Context:** executes the *claims* half of ADR-014,
which ratified folding claims + policy into ktayl-core (policy first). With policy **done** (ADR-015/016,
live dev+prod, Go PAS retired), this ADR pressure-tests the claims fold **before** building it — and
concludes it is **premature**. Keep `ktayl-claims` independent for now; revisit against explicit preconditions.

## Context — claims is NOT the policy PAS
The policy fold was clean: the Go PAS was a **self-contained system of record** for a thin registry, one
synchronous consumer, a straight Go→Java reimplementation. **Claims is architecturally the opposite:**

- `ktayl-claims` is a **modern Anti-Corruption Layer / strangler** (Java 21 + Spring Boot) **over the frozen,
  external GlobalCore legacy** (Java 8 · SOAP · nightly batch · **MySQL 8** · stored procedures — runs
  **outside k8s**, embedded at `ktayl-claims/legacy/globalcore`). **The claims system of record is GlobalCore**,
  not the service.
- The service's shape is: **SOAP→JSON** writes to GlobalCore · **Debezium (MySQL binlog) → NATS** CDC · a
  **disposable Postgres CQRS read-model** projected from CDC (reads never hit the legacy, ADR-004/008) · an
  Angular/Next workbench · governed read-only AI via ACL SQL-tools.
- **It is still in DESIGN** — the SA artefact set is `Status: DRAFT for review`; `AGENTS.md` mandates *"No
  Claims build until the plan + SA set are validated"*; only a thin slice (Property: FNOL→reserve→settle→closed)
  is scoped. It is **not** a mature, live, stable production service the way the PAS was.
- `architecture-strategy.md` already classifies claims as a **"deliberate, justified extraction, kept"**
  (ACL/strangler over the frozen legacy — different stack + lifecycle). ADR-014 said "fold eventually,
  post-legacy-strangler." These are in tension; this ADR resolves it.

## The extraction test, run honestly (architecture-strategy.md)
Fold-vs-keep is decided by the extraction criteria, not by ADR-014 momentum. Scoring claims:

| Signal | Verdict | Direction |
|---|---|---|
| Same tech stack (Java 21 + Spring Boot = ktayl-core) | yes | → **fold** (mechanically a Java→Java absorption) |
| Solo dev → per-service tax (CI/Kargo/CNPG/Helm/netpol/QA/docs) | yes | → **fold** (consolidation saves tax) |
| **Couples to an EXTERNAL frozen legacy** (SOAP + MySQL-CDC + Debezium, outside k8s) | strong | → **keep** (alien integration shape) |
| **Strong data isolation** (SoR is external GlobalCore; read-model disposable) | strong | → **keep** |
| **Different lifecycle & SLA** (async batch / CDC / eventual-consistency, not request-path transactional) | yes | → **keep** |
| Clear, stable business boundary (Claims) | yes | → **keep** (extractable either way) |
| Flagship **portfolio value as a standalone modernization showcase** (an ACL over a real legacy core — the enterprise-modernization pattern) | strong | → **keep** |

**Several strong "keep" signals + two "fold" signals.** Per the rule (*"one or two yes ≠ a service" — but here
the yes's are the KEEP side*), the honest reading is **keep, not fold** — at least until the "fold" economics
(per-service tax) actually bite and the "keep" risks (folding an in-design moving target, importing CDC/SOAP
infra) are retired.

## Decision — DEFER the claims fold
**Keep `ktayl-claims` a standalone service for now.** Do **not** absorb it into ktayl-core yet. This refines
ADR-014: policy folded (done); **claims is deferred behind explicit preconditions**, because the reasons that
made the policy fold a clear win do not hold for claims today.

### Options considered
- **A — Fold now** (absorb domain+application+read-model as a `claims` module; SOAP ACL + Debezium consumer
  become infra adapters; GlobalCore stays external). *Rejected now:* folds a DRAFT, unbuilt strangler (a moving
  target); imports Debezium-CDC-consumer + SOAP-to-external + a CQRS read-model into the monolith (heavy, alien);
  and dilutes claims' standalone-modernization-showcase value. High risk, low current payoff.
- **B — Defer / keep separate, with preconditions** *(chosen)*. Honors the extraction test + architecture-strategy's
  existing "deliberate extraction" classification; lets claims be **built and proven as the strangler first**
  (its primary value); revisits the fold only when the economics justify it.
- **C — Permanent separate service.** Plausible (the keep-signals are real), but premature to commit to "never" —
  once claims is mature and the per-service tax is painful, a Java→Java absorption may become worthwhile. B keeps
  that door open with a written trigger; C closes it prematurely.

### Preconditions to revisit the fold (ALL should hold before re-opening Option A)
1. **Claims is built + stable** — the SA set validated, the thin slice live on dev+prod, no longer DRAFT/moving.
2. **The GlobalCore strangler is mature** — the SOAP/CDC/read-model contracts are settled (ADR-014's
   "post-legacy-strangler" gate genuinely met), so absorbing the ACL isn't chasing a changing design.
3. **The per-service tax is actually painful** — the consolidation saving is real and felt, not hypothetical.
4. **A fold wouldn't regress the showcase** — the standalone "ACL-over-a-legacy-core" story is captured as
   evidence (case study) so folding doesn't erase it.

When these hold, re-open with the full fold design (mirroring ADR-015): a `claims` module (domain + application
+ CQRS read-model as `templates/` extras), the SOAP ACL + Debezium consumer as infrastructure adapters, GlobalCore
**staying external + frozen**, a strangler/backfill plan, and the Phase-6 acceptance gate. See the SPEC.

## Consequences
- **Positive:** no premature, risky absorption of an in-design strangler; claims keeps its flagship value as a
  standalone enterprise-modernization system; the monolith stays free of external-legacy CDC/SOAP infra until
  there's a proven reason to take it on. The decision is **evidence-based (the extraction test)**, not momentum.
- **Negative / accepted:** ADR-014's "fold both" is only half-delivered (policy); claims consolidation is
  deferred, so the per-service tax for claims persists. Accepted — it's the lesser cost.
- **One concrete now-action (independent of the fold):** claims' SA shows a **"coverage read → ktayl-policy-service"**
  dependency. That service is **retired** (ADR-015) — when claims is built, its coverage-read must target the
  **ktayl-core `policy` module** (`http://ktayl-core.ktayl-core-prod.svc:80/v1/policies?policy_number=…`), accepted
  via the multi-issuer allowlist (ADR-016). Recorded here so the stale pointer isn't missed at claims build time.

Related: ADR-014 (the fold decision this refines), ADR-015 (the policy fold — the "clean fold" contrast),
ADR-016 (the M2M auth claims will reuse), the SPEC `docs/specs/spec-claims-fold/SPEC.md`,
`architecture-strategy.md` (the extraction test + the "deliberate extraction" classification),
ktayl-claims `docs/architecture/` (the strangler SA set). [[project_policy_fold_into_core]].
