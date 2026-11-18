-- FINCLOSE-01: the finance module OWNS the `finance` schema (ADR-006 schema-per-module). This story is a
-- READ layer over billing's GL outbox + the ERPNext ledger — it introduces NO second ledger. The only table
-- is the accounting-period REGISTRY: a period is `open` until explicitly closed (the close action + lock is
-- FINCLOSE-02). FINCLOSE-01 only READS this — a period with no row is implicitly open. Flyway's default
-- schema is `billing` (its history lives there), so this migration explicitly creates + qualifies `finance`.
CREATE SCHEMA IF NOT EXISTS finance;

CREATE TABLE finance.accounting_period (
    period     varchar(7)  PRIMARY KEY,                               -- 'YYYY-MM'
    status     varchar(16) NOT NULL DEFAULT 'open'
                   CHECK (status IN ('open', 'closed')),
    created_at timestamptz NOT NULL DEFAULT now()
);
