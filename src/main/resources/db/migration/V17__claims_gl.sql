-- CLM-FIN-01 (ADR-021): the claims-GL module OWNS the `claimsgl` schema (ADR-006 schema-per-module).
-- This module is a thin, one-way GL-posting seam: it consumes claim financial events and posts balanced
-- Journal Entries to ERPNext via a transactional outbox (mirrors billing's BILL-014 ledger_outbox). It does
-- NOT own the claim or the reserve/payment workflow — that stays in ktayl-claims. Flyway's default schema is
-- `billing` (its history lives there), so this migration explicitly creates + qualifies `claimsgl`.
CREATE SCHEMA IF NOT EXISTS claimsgl;

CREATE TABLE claimsgl.claim_ledger_outbox (
    id           uuid         PRIMARY KEY,
    event_type   varchar(32)  NOT NULL,                       -- RESERVE_MOVEMENT | CLAIM_PAYMENT
    -- the claim financial event id = the JE remark + idempotency ref. UNIQUE so a redelivered event
    -- (NATS is at-least-once) never queues a duplicate JE — DB-level idempotency.
    reference    varchar(80)  NOT NULL UNIQUE,
    claim_number varchar(64)  NOT NULL,
    -- SIGNED eurocents: a reserve movement may be negative (a downward adjustment / release); the JE factory
    -- flips debit/credit accordingly. A payment is always > 0. (Contrast billing's amount_minor > 0 CHECK.)
    amount_minor bigint       NOT NULL CHECK (amount_minor <> 0),
    currency     varchar(3)   NOT NULL,
    status       varchar(16)  NOT NULL DEFAULT 'pending',     -- pending | posted
    attempts     int          NOT NULL DEFAULT 0,
    posted_ref   varchar(140),                                -- the ERPNext Journal Entry name, once posted
    last_error   varchar(500),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    posted_at    timestamptz
);
CREATE INDEX idx_claim_ledger_outbox_pending ON claimsgl.claim_ledger_outbox (status) WHERE status = 'pending';
