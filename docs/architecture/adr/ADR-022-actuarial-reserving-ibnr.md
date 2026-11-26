# ADR-022 — Actuarial reserving (IBNR): chain-ladder + Bornhuetter-Ferguson in the finance module, maker-checker to the GL

**Status:** Accepted (2026-11-26) · **Owner:** SA/TL · **Context:** the Tier-1 revenue/liability spine needs
**portfolio-level reserving** — not just the per-claim *case* reserves `ktayl-claims` already sets/adjusts/
approves, but the **IBNR** (Incurred But Not Reported) provision for losses that have occurred but aren't yet
in the claim system. Without it the insurer's liabilities are understated and the P&L is wrong. `ktayl-core`
already owns the GL seam (billing BILL-014, claims ADR-021) and the finance **close/reconciliation** read layer
(ADR-018/019); reserving is the natural next finance capability. This ADR records *where* actuarial reserving
lives, *which methods*, and *how* an estimate becomes a booked number.

## Decision
Build **FIN-03 actuarial reserving as a capability of the `finance` module** in `ktayl-core` — the pure
actuarial engine + a maker-checker review + a GL booking seam — **not** a separate actuarial service.

1. **It lives in `finance`, not a new service.** Reserving is a *read/estimate layer* over loss data plus a GL
   posting — exactly the finance module's lane (it already does close + reconciliation + reads billing's
   ledger view). A standalone "actuarial service" would be premature (one consumer, no independent lifecycle);
   revisit only if reserving grows its own cadence/stakeholders (the same need-first test as every module).

2. **Two methods, both pure domain: Chain-Ladder + Bornhuetter-Ferguson.** Chain-Ladder (volume-weighted
   link ratios → CDF → ultimate → IBNR) is the workhorse; **Bornhuetter-Ferguson** blends the chain-ladder
   *pattern* with an *a-priori* expected ultimate and is the standard choice for **immature** accident periods,
   where chain-ladder is unstable (a thin early diagonal × a large CDF leverages noise into a huge ultimate).
   Both are **pure functions** of a `LossTriangle` (`domain.reserving`, no Spring/DB/IO) → unit-tested against
   a hand-computed textbook triangle (factors 2.0/1.5/1.25 → total IBNR 775). This is the BC02 evidence.

3. **The triangle is finance-owned, loaded, not derived from the claims GL seam.** A loss-development triangle
   (accident period × development lag, cumulative paid/incurred) is **loaded** into `finance.loss_triangle_cell`
   via an API, **not** aggregated across the module boundary from `claimsgl` (ADR-006 — a module never reads
   another's tables). This is also how it works in practice: triangles come from a data warehouse, not the
   transactional ledger, and a small book's ledger is far too thin for a statistically meaningful triangle.
   A later enhancement may *derive* a triangle from claim financial history via a published view — a separate
   decision.

4. **An estimate is not a booking — it passes maker-checker first (threat T7).** A maker submits an IBNR
   estimate as a **PENDING** `reserve_review`; an **independent checker (≠ maker)** approves or rejects it. The
   segregation-of-duties rule and the must-be-PENDING guard are **invariants on the review entity** (pure,
   unit-testable), not controller checks. A reserve directly hits the P&L, so booking it is a governed act —
   the same four-eyes control as claims reserve-approval (ADR-021 workflow) and the finance close (ADR-019).

5. **On approval, the signed IBNR _movement_ is booked via a transactional outbox.** The booking is the
   **delta** vs the portfolio's last approved position (reserves move period to period), not the gross IBNR —
   so re-estimating and re-approving books only the change. The movement is enqueued to
   `finance.reserve_movement_outbox` in the **same transaction** as the approval, then drained to ERPNext
   (**DR _Change in IBNR_** (P&L) / **CR _IBNR Provision_** (liability); reversed for a release). This reuses
   the ADR-021 outbox/drainer/idempotent-ERPNext pattern verbatim (WET-first duplication — the finance module
   can't reach into claims'/billing's ledger package; extract on the 3rd+ consumer). Idempotent by review id;
   a GL outage leaves the row pending (reserving is never blocked).

## Consequences
- **+** Closes the last Tier-1 gap: the insurer can now **reserve for** the book, not just set case reserves.
  Ultimate/IBNR is auditable (per-accident-period breakdown stored on the review), governed (four-eyes), and
  lands in the GL as a real provision movement.
- **+** The actuarial math is isolated, pure, and pinned to textbook values — cheap to extend (Mack/bootstrap
  stderr, tail factors, a-priori from premium × ELR) without touching persistence or the GL seam.
- **−** Triangles are loaded, not yet auto-derived from claim history → an operational data-load step. Accepted
  (need-first); the derive-from-claims path is a later, separate decision.
- **−** A fourth near-copy of the GL outbox/JE pattern (billing, claims, now reserving). Accepted per ADR-021's
  WET-first rule; the shared-GL-library extraction is the tracked follow-up when a 3rd+ consumer makes the
  abstraction obvious.

## Scope (what this is NOT)
Not a full actuarial platform: no stochastic reserve ranges (Mack/bootstrap), no tail extrapolation beyond the
observed triangle, no premium/ELR-driven a-priori automation, no Solvency-II technical-provision discounting.
Those are deferred enhancements on top of this engine, not blockers to closing FIN-03.
