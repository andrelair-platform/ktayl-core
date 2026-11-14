-- BILL-013c: dunning — the non-payment path. A SEPA Direct Debit can FAIL (insufficient funds, closed
-- account, mandate revoked); Stripe sends payment_intent.payment_failed. We mark the payment failed, keep
-- the installment OPEN, and track the collection struggle in a dunning_case (one per installment). After
-- `lapse-threshold` failed attempts the case becomes `lapse_pending` and a PolicyLapseRecommended event is
-- emitted — a RECOMMENDATION for human/regulatory review, never an automatic policy cancellation. A later
-- successful payment resolves the case.
CREATE TABLE dunning_case (
    id             uuid         PRIMARY KEY,
    installment_id uuid         NOT NULL UNIQUE REFERENCES installment (id),
    policy_number  varchar(64)  NOT NULL,
    status         varchar(20)  NOT NULL DEFAULT 'open',      -- open | lapse_pending | resolved
    attempts       int          NOT NULL DEFAULT 0,
    last_reason    varchar(300),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_dunning_status ON dunning_case (status);
CREATE INDEX idx_dunning_policy ON dunning_case (policy_number);
