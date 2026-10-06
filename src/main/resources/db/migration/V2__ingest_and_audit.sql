-- BILL-011: the bound-premium ingest record + an append-only audit log (billing schema).

-- One row per bound policy ingested from the Underwriting bound-risk event (keyed by policy_number,
-- so a redelivered/replayed event is an idempotent no-op). premium_minor = eurocents (integer money).
CREATE TABLE ingested_policy (
    policy_number   varchar(64)  PRIMARY KEY,
    premium_minor   bigint       NOT NULL CHECK (premium_minor > 0),
    currency        varchar(3)   NOT NULL,
    product_code    varchar(64)  NOT NULL,
    effective_date  date         NOT NULL,
    expiry_date     date         NOT NULL,
    submission_id   varchar(64),
    quote_id        varchar(64),
    status          varchar(16)  NOT NULL DEFAULT 'ingested',
    ingested_at     timestamptz  NOT NULL DEFAULT now()
);

-- Append-only audit of billing state changes (who/what/when). The DB-level rewrite-rule hardening
-- (reject UPDATE/DELETE, like ktayl-iam) is BILL-015; for now the code path only ever INSERTs.
CREATE TABLE audit_log (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity      varchar(64)  NOT NULL,
    entity_id   varchar(128) NOT NULL,
    action      varchar(32)  NOT NULL,
    actor       varchar(128) NOT NULL,
    detail      text,
    at          timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_log (entity, entity_id);
