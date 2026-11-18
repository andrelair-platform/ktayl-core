-- FINCLOSE-02: the period close/reopen AUDIT — append-only, in the finance schema (ADR-006). Each close or
-- reopen appends one immutable row; rows are NEVER updated or deleted (the attested snapshot is the evidence).
-- `accounting_period.status` (V14) holds the current OPEN/CLOSED state; this is the history + the attestation.
-- The close is a CONTROL/attestation, not a physical lock on billing (ADR-019): finance cannot block billing's
-- outbox (that would be a module cycle), so a close records that the subledger was settled + reconciled at
-- close time, and the reconciliation's post-close-movement flag detects any later change.
CREATE TABLE finance.period_close_audit (
    id                     uuid         PRIMARY KEY,
    period                 varchar(7)   NOT NULL,                       -- 'YYYY-MM'
    action                 varchar(16)  NOT NULL CHECK (action IN ('CLOSED', 'REOPENED')),
    actor                  varchar(128) NOT NULL,                       -- the authenticated principal (never a placeholder)
    at                     timestamptz  NOT NULL DEFAULT now(),
    -- the attested snapshot at close time (null for a REOPENED row, which carries no reconciliation snapshot):
    subledger_posted_minor bigint,
    gl_posted_minor        bigint,
    drift_minor            bigint,
    gl_status              varchar(16),
    forced                 boolean      NOT NULL DEFAULT false,
    reason                 varchar(500)
);

CREATE INDEX idx_period_close_audit_period ON finance.period_close_audit (period, at);
