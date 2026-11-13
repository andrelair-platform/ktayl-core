# ADR-011 — Product Configure: insurance products as data, not code

**Status:** Accepted (2026-11-13 — ratified) · **Owner:** SA/TL · Lives in the **Product & Policy** context (ADR-005).
**Prompted by:** the InsureMO reference architecture (its "Product Configure" tier) — the one idea from that
middle-office model worth committing.

## Context
In insurance, **the product IS the configuration** — coverages, limits, deductibles, rating factors,
eligibility rules, forms, versions, effective dates. The naïve build hard-codes each line of business
(Marine, Engineering, Financial Lines…) into `if`/`switch` logic across underwriting, policy and billing.
That is the mistake insurers discover painfully late: **launching or amending a product requires a code
change, a deploy, and a regression across three modules** — slow, risky, and it couples the business
calendar to the release calendar. A regulated insurer ships product changes constantly (regulatory
updates, new lines, rate revisions); the architecture must make that a **data change, not a deploy**.

## Decision
**Model insurance products as versioned data/configuration, interpreted at runtime — not as code.** A
**`product` capability** inside the **Product & Policy** context owns the product definitions; underwriting,
policy and billing **read** them, they do not hard-code them.

- **Product definition = data:** a versioned product model (coverages, limits/deductibles, rating factors,
  eligibility/validation rules, forms, commission rules) stored in the `policy` schema (the Product&Policy
  context owns it). Rules that must be author-editable use a **rules representation** (e.g. a decision
  table / expression DSL / embedded rule engine), not Java branches.
- **Interpreted at runtime:** UW rating, policy issuance and billing **look up the product version** and
  apply its config. Adding a coverage or revising a rate = a new **product version** (data), effective-dated
  — **no deploy**, no cross-module code change.
- **Versioned + effective-dated:** every in-force policy pins the product **version** it was written on, so
  a product change never retroactively mutates existing contracts (a hard insurance invariant). New
  business/renewals pick up the new version by effective date.
- **Published read contract:** `product` exposes a `ProductCatalog`/`ProductVersion` published API (ADR-005)
  that UW/Policy/Billing consume; they never read product tables directly.
- **Governance:** a product change is **authored + reviewed as data** (its own approval workflow + audit),
  not a PR to `ktayl-core`. Code changes only when the *engine* (how rules are interpreted) changes.

## Why (vs the alternatives)
- **vs hard-coded products (the default mistake):** every new line/rate = a deploy + a 3-module regression;
  the business is blocked on engineering. Rejected.
- **vs adopting a full COTS product engine (e.g. InsureMO/Guidewire PC):** valid, and the honest fast path
  to a "real" insurer — but it's an *adopt* decision (ADR-008 / the adopt-vs-build gate), heavy, and not the
  point of a build-to-learn core. We build a **right-sized** product-config capability, and keep *adopt* as
  the escape hatch if a real product-authoring team ever needs it.
- **vs a separate "Product Configure" service** (as InsureMO layers it): no — that reintroduces the
  distributed monolith. It is a **capability inside the Product&Policy context**, read in-process by sibling
  contexts via the published catalog API + events. (This is the key adaptation of the reference model to
  ADR-004/007: keep the *layer* as a module, not a deployable.)

## Consequences
- **Positive:** new products/rate revisions ship as **data** (fast, low-risk, no deploy); existing policies
  are immune (version-pinned); the business owns the product calendar; a strong, demonstrable insurance
  capability most hand-built systems lack.
- **Negative / accepted:** up-front cost to build the product model + a rules representation + an authoring/
  approval workflow (more than hard-coding one line) — pays back by the **second** product change. Complexity
  of a rule engine/DSL must be kept proportionate (start with versioned config + decision tables; add a full
  rule engine only if authoring complexity demands it — need-first).
- **Scope:** MVP = versioned product config + effective-dating + the published catalog API, consumed by UW
  rating. The authoring UI + full rule DSL are later increments.

## Follow-ups
- Data model for `product` / `product_version` in the `policy` schema (ADR-006 ownership).
- The `ProductCatalog` published contract + a `ProductVersionPublished` event (ADR-005/007).
- Reflected in the SI target architecture diagram (`docs/diagrams/ktayl_si_target.py`).
