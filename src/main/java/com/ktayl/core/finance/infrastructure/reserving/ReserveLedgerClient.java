package com.ktayl.core.finance.infrastructure.reserving;

/**
 * Outbound port to the general ledger (ERPNext) for IBNR reserve-movement postings (FIN-03, ADR-022). The
 * module depends on "post a balanced JE", not on ERPNext/Frappe — the HTTP client lives only in
 * {@link ErpNextReserveLedgerClient}, active solely when ERPNext API credentials are configured.
 */
public interface ReserveLedgerClient {

    /** Post a balanced JE, idempotently keyed by {@code idempotencyRef} (a retry returns the existing JE). */
    String postJournalEntry(ReserveJournalEntry entry, String idempotencyRef);
}
