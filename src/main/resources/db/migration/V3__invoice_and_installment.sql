-- BILL-012: the premium invoice + its installment schedule (billing schema).
-- One invoice per bound policy (UNIQUE policy_number = the idempotency backstop at the DB level).
-- Money is eurocents (bigint). invoice.total_minor MUST equal the sum of its installments.

CREATE TABLE invoice (
    id            uuid         PRIMARY KEY,
    policy_number varchar(64)  NOT NULL UNIQUE REFERENCES ingested_policy (policy_number),
    currency      char(3)      NOT NULL,
    total_minor   bigint       NOT NULL CHECK (total_minor > 0),
    status        varchar(16)  NOT NULL DEFAULT 'issued',   -- issued | settled | void
    issued_at     timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE installment (
    id           uuid        PRIMARY KEY,
    invoice_id   uuid        NOT NULL REFERENCES invoice (id),
    seq          int         NOT NULL,
    due_date     date        NOT NULL,
    amount_minor bigint      NOT NULL CHECK (amount_minor > 0),
    status       varchar(16) NOT NULL DEFAULT 'open',        -- open | paid
    paid_at      timestamptz,
    UNIQUE (invoice_id, seq)
);
CREATE INDEX idx_installment_invoice ON installment (invoice_id);
