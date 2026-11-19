package com.ktayl.core.finance.api;

import java.time.YearMonth;
import java.util.List;

/**
 * The finance module's published inbound port (FINCLOSE-01) — the period-close read operations, driven by the
 * REST adapter and callable in-process by other modules. Expressed in java types only; both operations are
 * read-only (no close/lock yet — that is FINCLOSE-02).
 */
public interface FinanceCloseOperations {

    /**
     * Reconcile a period: the billing subledger's POSTED total vs the GL (ERPNext) total, the drift between
     * them, and the count/total of unposted ("stuck") GL postings. If the GL is unreachable, the GL side is
     * marked {@code UNAVAILABLE} (never an error).
     */
    PeriodReconciliation reconcile(YearMonth period);

    /** The period premium register: gross premium invoiced, cash received, receivable outstanding. */
    PremiumRegister register(YearMonth period);

    /**
     * The close dashboard (FINCLOSE-03): the reconciliation for every period in the inclusive range
     * {@code [from, to]}, oldest first. The caller bounds the range (the web adapter caps it).
     */
    List<PeriodReconciliation> dashboard(YearMonth from, YearMonth to);
}
