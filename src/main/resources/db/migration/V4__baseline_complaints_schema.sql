-- CMPL-010 baseline. The complaints module OWNS the `complaints` schema (ADR-006 schema-per-module).
-- Flyway's default-schema is `billing` (its history table lives there), so this migration EXPLICITLY
-- creates and qualifies the `complaints` schema — one Flyway instance, multiple module-owned schemas.
-- The complaints module never reads another module's schema (ADR-006). Domain tables (complaint,
-- audit_log) arrive with their code in CMPL-011/012; a marker makes the baseline explicit + non-empty.
CREATE SCHEMA IF NOT EXISTS complaints;

CREATE TABLE IF NOT EXISTS complaints.schema_marker (
    module      varchar(64) PRIMARY KEY,
    initialized timestamptz NOT NULL DEFAULT now()
);

INSERT INTO complaints.schema_marker (module) VALUES ('complaints')
    ON CONFLICT (module) DO NOTHING;
