package com.ktayl.core.finance.domain;

import java.time.YearMonth;

/**
 * Outbound port to the accounting-period registry (FINCLOSE-01). A period is {@code OPEN} until explicitly
 * closed; FINCLOSE-01 only reads this (the close action + lock is FINCLOSE-02). A period with no registry row
 * is implicitly {@code OPEN}.
 */
public interface AccountingPeriodPort {

    /** {@code OPEN} or {@code CLOSED} for {@code period} (defaults to {@code OPEN} when no row exists). */
    String statusOf(YearMonth period);
}
