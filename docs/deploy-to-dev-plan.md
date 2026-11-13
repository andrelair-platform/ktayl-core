# ktayl-core — first dev deploy + live QA (CMPL-014 / BILL-016) — runbook

> **Nature:** the first deploy of the *whole* ktayl-core deployable — Billing **and** Complaints ship in
> one app (modular monolith). So billing's deferred **BILL-016** ≡ complaints' **CMPL-014**. The app is
> stateless **+ owns its CNPG Postgres**. Reference implementation throughout: **`ktayl-policy-service`**
> (wrapper chart + CNPG `templates/` extra + Kargo git-Warehouse).
>
> **Non-destruction + safety:** additive only; prod promotion is explicitly OUT of this slice (a later
> CODEOWNERS/Kargo step). The Authentik changes (Phase 4) are the one outward/manual chunk.

## Phase 0 — confirm (before building)

- [ ] **Authentik provider.** `spring.security…issuer-uri` points at `…/application/o/ktayl-core/` — confirm
      an Authentik OAuth2/OIDC provider+app `ktayl-core` exists (or create it).
- [ ] **Groups claim.** Add the Authentik *groups* scope mapping to the ktayl-core provider so the token
      carries a `groups` claim — **without it every `/api/complaints/**` request is 403.**
- [ ] Create groups **`Complaints Handler`** (complaints authz) + **`Finance`** (billing authz); add the operator.
- [x] ghcr `ktayl-core` package is **public** → no `ghcr-pull` imagePullSecret needed.

## Phase 1 — database + secrets (gitops, CODEOWNERS-gated)

- [ ] **CNPG `Cluster`** (1 instance on dev) as a `templates/` extra in the wrapper (the sanctioned
      stateful-extra pattern — mirrors `ktayl-policy-service`'s CNPG). Flyway creates the `billing` +
      `complaints` schemas **on boot** (migrations-on-boot); the DB only provides the database + role.
- [ ] **ESO `ExternalSecret`** → `DATABASE_URL/USER/PASSWORD`; Stripe **test** keys (billing) from Vault.
      Add the ESO SSA `ignoreDifferences` (+ `RespectIgnoreDifferences=true`) to the ArgoCD app, or it stays
      permanently OutOfSync.

## Phase 2 — GAP wrapper chart + dev ArgoCD app

- [ ] `services/ktayl-core/helm/` from the `ktayl-policy-service` template: `Chart.yaml` (library dep
      `minicloud-app-deployment`, pinned) + committed `Chart.lock`; `values.yaml` + `values-dev.yaml` /
      `values-prod.yaml`; CNPG + `Certificate` + SSO `Ingress` as `templates/` extras; **`helm.releaseName:
      ktayl-core`** (mandatory). `.gitignore` the vendored `charts/`; `.helmignore` must NOT list `charts/`.
- [ ] **Image tag block-style** in both values overlays (`image:`\n`  tag: "<sha>"`) — Kargo's yaml-update
      cannot mutate flow style.
- [ ] Env per overlay: `AUTHENTIK_ISSUER`, OTEL endpoint (app default), `LOG_STRUCTURED_FORMAT=ecs`,
      `BILLING_NATS_URL` **blank on dev** (single-consumer discipline), `COMPLAINTS_ACK_WINDOW=P2D`.
- [ ] **ArgoCD `ktayl-core-dev`** app (single Helm source, auto-sync). Namespaces already in the AppProject.
      (Prod app is **deferred** — created at promotion time, not now.)

## Phase 3 — Kargo (git-Warehouse)

- [ ] `services/ktayl-core/kargo/` — **git** Warehouse (NOT NewestBuild: the temurin base layer breaks
      ranking — the ktayl-policy-service/claims trap), Freight keyed on the commit; `excludePaths` mirroring
      CI's `paths-ignore` (phantom-commit guard). dev Stage `sources.direct`, prod Stage `sources.stages:[dev]`.
      Per-project `kargo-git-credentials` ESO. (Auto-discovered by the `kargo-projects` ApplicationSet.)

## Phase 4 — Authentik (MANUAL / outward)

- [ ] OIDC client/secret, redirect + scope config, the groups scope mapping, and the two groups (Phase 0).
      The one chunk not driven from gitops. Verify the **decoded token carries `groups`** before QA.

## Phase 5 — network + ingress

- [ ] Default-deny netpols + egress: DNS, CNPG, **otelcol:4318** (traces), ERPNext (GL), Stripe (public TLS),
      Authentik JWKS; ingress from ingress-nginx. `Ingress` `ktayl-core.10.0.0.200.nip.io` + TLS via
      ClusterIssuer `minicloud-ca`. Internal only — no Cloudflare tunnel for QA.

## Phase 6 — live QA gate (the CMPL-014 DoD)

Adversarial pass on the converged pod — the 12 areas:
1. happy paths: register → acknowledge → assign → resolve → close
2. validation → **422** (blank summary, unknown channel, blank assignee/note)
3. not-found → **404**
4. **state guards → 409** (out-of-order, re-close, op on CLOSED)
5. idempotency (same `Idempotency-Key` → same complaint, no dup)
6. **authz**: no token → **401**; token without `Complaints Handler` → **403**
7–9. **observability triad**: structured logs in **Loki**; a **trace in Tempo** for a register→close flow;
   `/actuator/prometheus` scrape works
10. **no 500s** anywhere
11. audit: a `complaints.audit_log` row per transition, actor = the token subject
12. billing smoke still green (shared deployable not regressed)

Fix → redeploy → re-verify on the converged pod. Closes the 🟠 traceability rows (CNFR-004, live
attribution, L4 smoke). **Green CI is not sufficient to promote — this live gate is the backstop.**

## Out of scope / risks

- **Prod promotion** — later CODEOWNERS/Kargo step, not this slice.
- Phases 1–3/5 are **CODEOWNERS-gated** (`services/*`, `apps/`) → reviewed PR(s).
- Highest-risk live snag: the Authentik groups-claim wiring (→ 403s). Decode the token first.
