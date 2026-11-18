package com.ktayl.core.finance.domain;

import java.time.YearMonth;

/**
 * Outbound port to mutate a period's lifecycle state in the registry (FINCLOSE-02). Upserts the
 * {@code accounting_period} row. The read side stays on {@link AccountingPeriodPort}.
 */
public interface PeriodLifecyclePort {

    /** Mark the period CLOSED (upsert the registry row). */
    void close(YearMonth period);

    /** Mark the period OPEN (upsert the registry row). */
    void reopen(YearMonth period);
}
