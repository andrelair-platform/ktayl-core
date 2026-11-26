-- FIN-03 (ADR-022): actuarial reserving in the finance module (board #14/#28). Chain-Ladder + Bornhuetter-
-- Ferguson IBNR estimation on loss-development triangles, with a maker-checker reserve review and a GL seam
-- that books the approved IBNR movement. Finance OWNS the `finance` schema (ADR-006); Flyway's default schema
-- is `billing`, so this migration qualifies `finance` explicitly.

-- The stored loss triangle, one row per cell. A triangle is identified by (portfolio, as_of, basis); cells are
-- the cumulative paid/incurred amount (eurocents) at each (accident_period, development_lag). The actuarial
-- shape invariant (upper-left staircase) is enforced in the domain (LossTriangle), not the DB — the DB only
-- guarantees one value per cell.
CREATE TABLE finance.loss_triangle_cell (
    id                uuid         PRIMARY KEY,
    portfolio         varchar(64)  NOT NULL,                 -- e.g. 'marine', 'property', or a whole-book key
    as_of             date         NOT NULL,                 -- the valuation (diagonal) date
    basis             varchar(16)  NOT NULL CHECK (basis IN ('PAID', 'INCURRED')),
    accident_period   varchar(16)  NOT NULL,                 -- when the loss occurred (e.g. '2024-Q1')
    development_lag   int          NOT NULL CHECK (development_lag >= 0),
    cumulative_minor  bigint       NOT NULL CHECK (cumulative_minor >= 0),
    currency          varchar(3)   NOT NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        varchar(128) NOT NULL,
    -- one value per cell; re-loading a cell overwrites via the app (delete+insert the triangle)
    CONSTRAINT uq_triangle_cell UNIQUE (portfolio, as_of, basis, accident_period, development_lag)
);
CREATE INDEX idx_triangle_cell_lookup ON finance.loss_triangle_cell (portfolio, as_of, basis);

-- The maker-checker reserve review. A maker submits an estimate (PENDING); an independent checker (≠ maker,
-- segregation of duties — threat T7) approves or rejects. On approval the signed IBNR movement vs the last
-- approved position is booked to the GL via the outbox below. The estimate snapshot (per-accident-period
-- breakdown) is stored as JSON for audit/display — the booked number is `estimated_ibnr_minor`.
CREATE TABLE finance.reserve_review (
    id                        uuid         PRIMARY KEY,
    portfolio                 varchar(64)  NOT NULL,
    as_of                     date         NOT NULL,
    basis                     varchar(16)  NOT NULL,
    method                    varchar(32)  NOT NULL CHECK (method IN ('CHAIN_LADDER', 'BORNHUETTER_FERGUSON')),
    currency                  varchar(3)   NOT NULL,
    latest_minor              bigint       NOT NULL,         -- incurred-to-date at as_of
    estimated_ultimate_minor  bigint       NOT NULL,
    estimated_ibnr_minor      bigint       NOT NULL,         -- the reserve being proposed (ultimate − latest)
    detail_json               text         NOT NULL,         -- per-accident-period breakdown (evidence)
    state                     varchar(16)  NOT NULL DEFAULT 'PENDING'
                                           CHECK (state IN ('PENDING', 'APPROVED', 'REJECTED')),
    maker                     varchar(128) NOT NULL,
    checker                   varchar(128),                  -- set on decision; must differ from maker
    booked_movement_minor     bigint,                        -- signed IBNR movement booked on approval (null until)
    reason                    varchar(500),                  -- required on reject, optional note on approve
    created_at                timestamptz  NOT NULL DEFAULT now(),
    decided_at                timestamptz
);
CREATE INDEX idx_reserve_review_portfolio ON finance.reserve_review (portfolio, created_at);
CREATE INDEX idx_reserve_review_state ON finance.reserve_review (state) WHERE state = 'PENDING';

-- The GL seam (transactional outbox, ADR-021/022). An approved review enqueues ONE row here (the signed IBNR
-- movement); the drainer posts a balanced JE to ERPNext (DR Change-in-IBNR / CR IBNR Provision, reversed for a
-- release). `reference` = the review id → DB-level idempotency, a redelivery/retry never double-books.
CREATE TABLE finance.reserve_movement_outbox (
    id           uuid         PRIMARY KEY,
    review_id    uuid         NOT NULL,
    portfolio    varchar(64)  NOT NULL,
    -- SIGNED eurocents: a reserve strengthening (> 0) or a release (< 0). A zero movement is never enqueued.
    amount_minor bigint       NOT NULL CHECK (amount_minor <> 0),
    currency     varchar(3)   NOT NULL,
    reference    varchar(80)  NOT NULL UNIQUE,               -- = review id; idempotency key
    status       varchar(16)  NOT NULL DEFAULT 'pending',    -- pending | posted
    attempts     int          NOT NULL DEFAULT 0,
    posted_ref   varchar(140),                               -- the ERPNext Journal Entry name, once posted
    last_error   varchar(500),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    posted_at    timestamptz
);
CREATE INDEX idx_reserve_movement_outbox_pending ON finance.reserve_movement_outbox (status) WHERE status = 'pending';
