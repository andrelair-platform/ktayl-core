-- BILL-013: premium payment via Stripe (TEST mode, SEPA Direct Debit) — async, webhook-captured (ADR-002).
-- A payment is initiated for an installment (idempotent by client_key = our Stripe idempotency-key), lands
-- `pending`, and flips to `succeeded` only on the signature-verified payment_intent.succeeded webhook.
CREATE TABLE payment (
    id             uuid         PRIMARY KEY,
    installment_id uuid         NOT NULL REFERENCES installment (id),
    client_key     varchar(80)  NOT NULL UNIQUE,          -- our idempotency key → Stripe idempotency-key
    psp_intent_id  varchar(128),                          -- Stripe PaymentIntent id (null until created)
    amount_minor   bigint       NOT NULL CHECK (amount_minor > 0),
    currency       varchar(3)   NOT NULL,
    status         varchar(16)  NOT NULL DEFAULT 'pending',   -- pending | succeeded | failed
    created_at     timestamptz  NOT NULL DEFAULT now(),
    captured_at    timestamptz
);
CREATE INDEX idx_payment_installment ON payment (installment_id);
CREATE INDEX idx_payment_intent      ON payment (psp_intent_id);

-- Webhook idempotency ledger: one row per Stripe event id, so a replayed/duplicated event is a no-op.
-- A webhook that arrives before its payment record is PARKED here (processed_at null) rather than dropped.
CREATE TABLE webhook_event (
    psp_event_id varchar(128) PRIMARY KEY,                 -- Stripe event id (evt_...)
    type         varchar(64)  NOT NULL,
    received_at  timestamptz  NOT NULL DEFAULT now(),
    processed_at timestamptz
);
