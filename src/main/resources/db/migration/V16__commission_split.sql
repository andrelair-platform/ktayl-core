-- BILL-012b: gross / net / commission split on the premium invoice.
-- This is the insurance-billing concept a generic GL (ERPNext) cannot model on its own:
--   gross      = what the policyholder owes            (= Σ installments = total_minor, UNCHANGED)
--   commission = the intermediary's (broker) cut       (a % of gross, in basis points)
--   net        = gross - commission                    (what the insurer actually earns)
-- The reconciliation invariant (net + commission = gross) is enforced by a CHECK, not just app code.
-- total_minor stays the gross (the policyholder is still billed/collected on gross) → zero impact on the
-- installment schedule, payment capture and dunning. Only the GL posting splits (DR receivable=gross /
-- CR income=net / CR commission payable=commission).

ALTER TABLE invoice
    ADD COLUMN gross_minor      bigint,
    ADD COLUMN commission_minor bigint,
    ADD COLUMN net_minor        bigint,
    ADD COLUMN commission_bps   int;

-- Backfill pre-split invoices: gross = the existing total, commission = 0, net = gross (no broker cut recorded).
UPDATE invoice
   SET gross_minor      = total_minor,
       commission_minor = 0,
       net_minor        = total_minor,
       commission_bps   = 0
 WHERE gross_minor IS NULL;

ALTER TABLE invoice
    ALTER COLUMN gross_minor      SET NOT NULL,
    ALTER COLUMN commission_minor SET NOT NULL,
    ALTER COLUMN net_minor        SET NOT NULL,
    ALTER COLUMN commission_bps   SET NOT NULL,
    ADD CONSTRAINT invoice_split_reconciles  CHECK (net_minor + commission_minor = gross_minor),
    ADD CONSTRAINT invoice_gross_is_total    CHECK (gross_minor = total_minor),
    ADD CONSTRAINT invoice_split_nonneg      CHECK (commission_minor >= 0 AND net_minor >= 0 AND commission_bps >= 0);

-- Carry the commission on the GL outbox row so the drainer can build the 3-leg Journal Entry.
-- amount_minor stays the GROSS (the finance premium register sums it as gross premium invoiced).
ALTER TABLE ledger_outbox ADD COLUMN commission_minor bigint NOT NULL DEFAULT 0;
