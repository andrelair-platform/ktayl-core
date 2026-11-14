-- BILL-014: the GL posting outbox (ADR-002 — transactional outbox). A billing event that must hit the
-- ledger (premium invoiced, payment captured) writes ONE row here IN THE SAME TRANSACTION as the business
-- state change, so the ledger intent and the business fact commit atomically (never a half-post). A
-- separate scheduled drainer posts `pending` rows to ERPNext as balanced Journal Entries and marks them
-- `posted` (idempotent by `posted_ref`); an ERPNext blip leaves the row `pending` → retried, billing never blocked.
CREATE TABLE ledger_outbox (
    id           uuid         PRIMARY KEY,
    event_type   varchar(32)  NOT NULL,                       -- PREMIUM_INVOICED | PAYMENT_CAPTURED
    reference    varchar(80)  NOT NULL,                       -- policy_number / payment id — the JE remark + idempotency ref
    amount_minor bigint       NOT NULL CHECK (amount_minor > 0),
    currency     varchar(3)   NOT NULL,
    status       varchar(16)  NOT NULL DEFAULT 'pending',     -- pending | posted
    attempts     int          NOT NULL DEFAULT 0,
    posted_ref   varchar(140),                                -- the ERPNext Journal Entry name, once posted
    last_error   varchar(500),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    posted_at    timestamptz,
    -- one ledger posting per (event_type, reference): the outbox write is idempotent at the DB level, so a
    -- replayed event (e.g. a redelivered PaymentCaptured) never queues a duplicate JE.
    UNIQUE (event_type, reference)
);
CREATE INDEX idx_ledger_outbox_pending ON ledger_outbox (status) WHERE status = 'pending';
