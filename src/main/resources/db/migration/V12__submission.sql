-- SUB-01: the submission module OWNS the `submission` schema (ADR-006 schema-per-module). The corporate
-- broker-submission front door — capture a submission + its exposure dossier (sites/values/loss-history/
-- geo-CAT), triage it, and route a qualified one to underwriting. Flyway's default-schema is `billing` (its
-- history lives there), so this migration EXPLICITLY creates + qualifies the `submission` schema. The module
-- never reads another module's schema (ADR-006); the holder is sanctions-screened at intake via the
-- screening module's published api (a HOLD flags the submission — it cannot be routed until cleared).
CREATE SCHEMA IF NOT EXISTS submission;

-- A broker submission: the request to quote a risk. status walks RECEIVED → TRIAGED → ROUTED (to underwriting)
-- or → DECLINED. screening_hold is set at intake when the holder matches sanctions (screening HOLD); a held
-- submission is still captured + triageable but CANNOT be routed until a compliance officer clears it (HITL).
CREATE TABLE submission.submission (
    id                 uuid        PRIMARY KEY,
    broker_ref         varchar(128) NOT NULL,       -- the broker's stable reference for this submission
    insured_name       varchar(256) NOT NULL,
    product_line       varchar(64)  NOT NULL,
    status             varchar(16)  NOT NULL CHECK (status IN ('RECEIVED','TRIAGED','ROUTED','DECLINED')),
    screening_hold     boolean      NOT NULL DEFAULT false,
    screening_alert_id uuid,                         -- the screening alert opened at intake (null unless held)
    routed_target      varchar(64),                  -- where it was routed (e.g. 'underwriting') — null until ROUTED
    received_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now()
);

-- The exposure dossier — one row per exposed site/location the submission covers (values, loss history,
-- geo-CAT flag). Referenced by submission_id (owned within this schema; never a cross-schema FK, ADR-006).
CREATE TABLE submission.exposure_item (
    id            uuid         PRIMARY KEY,
    submission_id uuid         NOT NULL REFERENCES submission.submission (id),
    site_name     varchar(256) NOT NULL,
    country       varchar(64)  NOT NULL,
    tiv_amount    numeric(18,2) NOT NULL,            -- total insured value at the site
    currency      varchar(8)   NOT NULL,
    occupancy     varchar(128),
    loss_history  text,
    cat_flag      boolean      NOT NULL DEFAULT false, -- geo-CAT exposed (flood/quake/wind etc.)
    created_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_exposure_item_submission ON submission.exposure_item (submission_id);

-- Append-only triage decisions — one immutable row per triage action (ROUTE / DECLINE / RETURN). A bigint
-- identity key; only ever INSERTed (no update/delete). Records the completeness score, appetite flag, and
-- the actor who decided, so the triage history survives any later data change.
CREATE TABLE submission.submission_triage (
    id                 bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    submission_id      uuid         NOT NULL REFERENCES submission.submission (id),
    completeness_score int,
    appetite_flag      varchar(32),
    decision           varchar(16)  NOT NULL CHECK (decision IN ('ROUTE','DECLINE','RETURN')),
    reason             text,
    actor              varchar(128) NOT NULL,
    decided_at         timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_submission_triage_submission ON submission.submission_triage (submission_id);
