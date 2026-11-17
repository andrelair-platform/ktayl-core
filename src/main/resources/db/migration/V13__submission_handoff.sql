-- SUB-02: hand a ROUTED submission off to the underwriting service (#12). Two additive columns complete the
-- exposure dossier for the handoff and record the underwriting submission created from it. The module still
-- owns only its own `submission` schema (ADR-006) — it never reaches into underwriting's tables; the handoff
-- is an outbound HTTP call (not a cross-schema read).

-- A site has a postcode — completes the dossier the underwriting contract requires (country + postcode).
-- Nullable: pre-SUB-02 exposure rows have none, and the handoff mapper falls back to "00000" when absent.
ALTER TABLE submission.exposure_item ADD COLUMN postcode varchar(16);

-- The underwriting submission id returned by a successful handoff (null until routed-and-handed-off; stays
-- null if the handoff failed, so the submission is retryable while ROUTED).
ALTER TABLE submission.submission ADD COLUMN uw_submission_ref varchar(64);
