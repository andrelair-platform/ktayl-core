-- CMPL-012 — lifecycle state for the complaint aggregate (acknowledge/assign/resolve/close). All nullable:
-- a complaint starts REGISTERED with none set, and each column is populated as its transition occurs. Maps
-- to com.ktayl.core.complaints.infrastructure.persistence.ComplaintJpaEntity (the domain aggregate holds
-- the same state but stays framework-pure, ADR-013).

ALTER TABLE complaints.complaint
    ADD COLUMN acknowledgement_due_at timestamptz,   -- placeholder next-action clock (NOT the statutory deadline)
    ADD COLUMN assigned_to            varchar(128),  -- handler, set on assign
    ADD COLUMN resolution_note        text;          -- set on resolve
