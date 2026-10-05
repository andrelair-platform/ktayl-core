# Contributing to ktayl-core

`ktayl-core` is the ktayl-solution **insurance-LOB modular monolith** (Spring Boot + Spring Modulith).
New insurance business domains are **modules inside this repo** (`src/main/java/.../<domain>/`), not new
services — see `.claude/rules/architecture-strategy.md` (modular-monolith-first) and
`docs/architecture.md`.

## Branch strategy (trunk-based)

- `main` — the only deploy branch. PR + GPG-signed commits required; CI builds `:<sha>` (dual-pushed
  Harbor + ghcr), signs (cosign) + SBOM; **Kargo** promotes dev → prod (CODEOWNERS-gated PR).
- Feature branches: `feat/…` `fix/…` `docs/…` `chore/…` → PR → `main`. Short-lived (auto-deleted on merge).

## Commits

- Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, `feat!:`) — drives release-please.
- **GPG-signed** (`git config commit.gpgsign true`). Kargo promotion PRs are merged with `--squash`
  (GitHub produces one signed squash commit).
- No `Co-Authored-By` lines.

## Module discipline (the thing that keeps this a modular monolith)

- One module = one business capability (high cohesion, SRP). A module talks to another **only** via its
  **public interface** or an **in-process application event** — never another module's internals, never
  another module's **tables/schema**, and never intra-app HTTP/queues.
- One Postgres, **schema-per-module** (Flyway migrations per module).
- **Enforced, not conventional:** a `ModularityTests` (`ApplicationModules.verify()`) fails the build on
  any boundary violation. Do not weaken it to make a cross-module shortcut compile.

## Definition of Done

Code + review + the repo tier's **full test layers** (L0 static, L1 unit, L2 integration via
Testcontainers, L3 contract vs the PAS/ERPNext collaborators, L4 smoke) + **org-site as-built doc
updated** + deployed to dev (promotable to prod via the Kargo/CODEOWNERS PR after the live QA gate).
No story is Done on unit tests alone. See the platform `.claude/rules/testing.md` + `qa-gate.md`.

## Before you build a story

This is a Path-C product — planning artefacts precede code: `docs/prd.md`, `docs/architecture.md`,
`docs/architecture/adr/*`, and the epic SPEC in `docs/specs/`. Run the readiness gate before coding.
