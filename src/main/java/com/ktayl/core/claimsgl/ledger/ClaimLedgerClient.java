package com.ktayl.core.claimsgl.ledger;

/**
 * Outbound port to the general ledger (ADR-021: ERPNext) for claims postings. Kept behind an interface so the
 * module depends on "post a balanced JE", not on ERPNext/Frappe — the HTTP client lives only in
 * {@link ErpNextClaimsLedgerClient}, active solely when ERPNext API credentials are configured.
 */
public interface ClaimLedgerClient {

    /**
     * Post a balanced Journal Entry, idempotently keyed by {@code idempotencyRef}: a retry with the same ref
     * must NOT create a duplicate JE — it returns the existing one. Returns the ledger's entry id/name.
     */
    String postJournalEntry(JournalEntry entry, String idempotencyRef);
}
