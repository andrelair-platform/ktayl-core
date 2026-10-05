# ADR-001 — ktayl-core stack: Spring Boot + Spring Modulith (Java 21)

- **Status:** Accepted (2026-10-05)
- **Owner:** AndreLiar (SA/TL)
- **Context gate:** `tech-stack-selection.md` (mandatory onboarding ADR) + `architecture-strategy.md`
  (modular-monolith-first). This is the product's founding technical decision.

## Context

ktayl-core is the ktayl-solution **insurance-LOB modular monolith** — the home for custom insurance
business domains as modules (Billing first, Distribution/CRM next). The stack was deliberately **not
pre-locked** (per `architecture-strategy.md`); it is chosen against the **actual first domain, Billing**.

Billing's hard part is **transactional correctness** — invoices, installment schedules, premium
receivable, **double-entry GL postings**, idempotency, reconciliation; money that must always balance.
It is *not* numeric/actuarial math (that lives in Underwriting, Python/FastAPI) and *not* throughput-bound.

The candidates evaluated (per the palette): **NestJS**, **Spring Boot + Spring Modulith**, **.NET**,
Python/FastAPI.

## Decision

**Java 21 + Spring Boot 3.5 + Spring Modulith 1.4**, Maven, Spring Data JPA + Flyway, PostgreSQL
**schema-per-module**. Cross-module communication via **public interfaces + Spring application events**;
boundaries **enforced** by a `ModularityTests` (`ApplicationModules.verify()`) that fails the build on a
violation. The **GL is adopted** (ERPNext) — ktayl-core posts Journal Entries, it does not implement a ledger.

## Why (vs the alternatives)

- **Primary rule — hard part lives in Java's home.** `tech-stack-selection.md`: *"heavy transactional
  domain, long-lived enterprise service → Java / Spring Boot."* Enterprise insurance is Java's home turf.
- **Enforced module boundaries.** Spring Modulith was built for this architecture: `@ApplicationModule`,
  application events, and compile/test-time **verification** of "no cross-module internals / no
  cross-schema reads." With NestJS that discipline is only conventional (human-enforced). The
  modular-monolith rule's hardest guarantee is delivered by **tooling**, not willpower.
- **Career/context fit.** The owner's apprenticeship (HDI, enterprise insurance) and target French market
  are Java/Spring-heavy; the portfolio is already rich in TS/Python/Go and conspicuously light on the
  dominant enterprise-insurance backend stack. This flagship spine fills that gap.
- **NestJS was the rejected default** — it won only on tie-breakers (consistency with `ktayl-iam` +
  solo-dev velocity), which `tech-stack-selection.md` lists as tie-breakers, not the primary driver.
  Boundary enforcement + transactional fit + career signal outweigh them for *this* product.
- **.NET** — valid transactional choice, but zero .NET in the stack, no sibling service, slower
  onboarding for a solo dev. **Python/FastAPI** — wrong: weakest at long-lived transactional rigor;
  Billing isn't numeric math.

## Consequences

**Positive:** enforced boundaries (the extraction escape-route stays honest); first-class transactions
(`@Transactional`), JPA + Flyway for a money domain; the enterprise-insurance stack in the portfolio.

**Negative / costs (accepted):**
- **More ceremony / lower solo velocity** than NestJS — mitigated by Spring Boot starters + keeping the
  MVP module thin.
- **Bigger cluster footprint** — a JVM pod needs more memory than a Node service. Size requests/limits
  for the namespace ResourceQuota (see `docs/prd.md` cost section); use a JRE (not JDK) runtime image.
- **Kargo must use the git-Warehouse model, NOT image/NewestBuild.** A `temurin`/JRE base image has a
  **fixed base-layer date**, so Kargo's `NewestBuild` can't rank tags and auto-promotion stalls silently
  (the exact ktayl-claims/policy-service trap in `gitops.md`). Key Freight on the **commit**
  (`commitFrom(...).ID[0:7]` == the CI short-SHA tag). This is decided now so the Kargo dir is born right.
- **New stack to operate** on the cluster (JVM build image, Maven CI) — one-time setup cost.

## Supply chain (CI Trivy CRITICAL gate)
- Keep the Spring Boot version **current** — an old patch pins old `tomcat-embed-core` + `spring-security`
  that carry fixed-available CRITICALs. BILL-010 shipped on **3.5.16** (was drafted on 3.4.1, which the
  Trivy gate correctly rejected for CVE-2025-24813 et al.).
- Boot 3.5.16 pins tomcat **10.1.55**; three 2026 tomcat CVEs (CVE-2026-65182/65905/68525) need **10.1.58**,
  so `tomcat.version` is overridden to **10.1.60** in the pom. Drop the override when a Boot patch pins ≥ 10.1.60.

## Follow-ups
- Image = multi-stage (temurin build → distroless/temurin-**jre** runtime, runs non-root for Gatekeeper).
- L2 = Testcontainers Postgres; L3 = contract tests vs the PAS OpenAPI + the ERPNext API (every mocked
  boundary backed by a contract test — `testing.md` mock discipline).
- release-please `release-type: simple` (+ `version.txt`); Maven version bumped by the release PR.
