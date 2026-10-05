-- BILL-010 baseline. The `billing` schema is created by Flyway (create-schemas=true); its history
-- table lives there too. Domain tables (invoice, installment, payment, webhook_event, ledger_outbox,
-- audit_log) arrive in later migrations (BILL-012..014). A marker table makes the baseline explicit
-- and the schema non-empty.
CREATE TABLE IF NOT EXISTS schema_marker (
    module      varchar(64) PRIMARY KEY,
    initialized timestamptz NOT NULL DEFAULT now()
);

INSERT INTO schema_marker (module) VALUES ('billing')
    ON CONFLICT (module) DO NOTHING;
