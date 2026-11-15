# SPEC — Claims fold (DEFERRED; the plan for when/if the preconditions are met)

**Status:** Deferred (decision ADR-017) · **Owner:** SA/TL. This is **not** an active build contract — it is
the *conditional* plan: **why claims is not folded now**, the **preconditions** to re-open it, and the
**phased design** to execute if they're met. Contrast: the policy fold (`spec-policy-fold`) is done; claims is
a fundamentally different (ACL-over-external-legacy) case — see ADR-017 for the analysis.

## Why deferred (one line)
`ktayl-claims` is an **ACL/strangler over the frozen external GlobalCore legacy** (SoR lives in GlobalCore,
not the service) and is **still in DESIGN (DRAFT, thin-slice)**. Folding an in-design strangler, and importing
its Debezium-CDC + SOAP-to-external + CQRS-read-model infra into the monolith, is premature. The extraction
test says **keep** for now (ADR-017).

## The now-action (independent of the fold) — do this when claims is built
Claims reads coverage from `ktayl-policy-service`, which is **retired** (ADR-015). Repoint claims' coverage-read
to the **ktayl-core `policy` module**: `GET http://ktayl-core.ktayl-core-prod.svc:80/v1/policies?policy_number=…`
(dev: `…ktayl-core.svc`), authenticated with a client-creds token accepted via the multi-issuer allowlist
(ADR-016 — add claims' issuer to `AUTHENTIK_TRUSTED_ISSUERS` if it differs). The REST contract is the same
carried-forward PAS shape, so it's a base-URL (+ token) change.

## Reopen gate — ALL must hold before this becomes an active build (ADR-017)
- [ ] Claims **built + stable** — SA set validated, thin slice live dev+prod, no longer DRAFT/moving.
- [ ] GlobalCore **strangler mature** — SOAP/CDC/read-model contracts settled ("post-legacy-strangler", ADR-014).
- [ ] Per-service **tax genuinely painful** — the consolidation saving is real, not hypothetical.
- [ ] The standalone **modernization-showcase story captured** as evidence (case study) so a fold doesn't erase it.

## The phased plan IF reopened (mirrors the policy fold, adapted for an ACL)
> Non-destructive strangler (ADR-009); claims stays running until each gate passes. **GlobalCore stays external
> + frozen throughout — it is NEVER folded** (it is the deliberately-legacy SoR).

1. **Module skeleton** — a `claims` CLOSED Spring Modulith module in ktayl-core (hexagonal, ADR-013):
   `domain` (Claim aggregate, FNOL/reserve/settle lifecycle, **server-side settlement/reserve authority** — the
   T7 control) · `application` (use cases) · `api` (`@NamedInterface` read surface) · `infrastructure`.
2. **Legacy adapters as infrastructure** — absorb, don't reinvent: the **SOAP→GlobalCore ACL client** and the
   **Debezium→NATS CDC consumer** become `infrastructure` adapters of the module (the one place that talks to the
   external legacy). Default-deny egress: only these may reach `globalcore:8080` (SOAP) + the MySQL binlog host.
3. **CQRS read-model** — the Postgres `claim_read` projection becomes the module's own schema (schema-per-module,
   ADR-006), rebuilt from CDC (disposable). Reads hit it, never the legacy (ADR-004/008).
4. **Repoint consumers** — the workbench/BFF + downstream event consumers point at ktayl-core; the coverage-read
   is already on the policy module (the now-action above). Underwriting/billing read claims in-process via the
   published `api` (the shared-kernel payoff).
5. **Soak + Phase-6 acceptance** — parity (the read-model projects the same GlobalCore state), the live QA gate,
   SLO check; **GlobalCore untouched** (no data migration — the SoR is unchanged).
6. **Retire the standalone `ktayl-claims` *service*** (not GlobalCore) — archive the repo, remove the ArgoCD
   app/Kargo, after the gate. Preserve its SA set + `_bmad` into ktayl-core (the policy-fold lesson:
   git-ignored `_bmad` is rescued BEFORE archive).

## Out of scope / invariants
- **GlobalCore is never folded, never edited, never migrated as part of this** (ADR-002/007 — MySQL→Postgres is a
  separate optional footnote, not a fold goal). The fold absorbs the *modern ACL service*, not the legacy.
- AI stays **read-only + governed** via ACL SQL-tools (ADR-005) — no change from folding.
- Reserve/settlement **authority is enforced server-side** in the module (carried from the ACL — T7); the legacy
  PL/SQL state-machine + audit remain authoritative in-DB (not the only guard).
