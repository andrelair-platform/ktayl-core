package com.ktayl.core.finance.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

/** Pure accounting-period time arithmetic (UTC). The period owns the half-open instant window [start, end). */
public final class AccountingPeriods {

    private AccountingPeriods() {}

    /** The exclusive upper bound of the period's instant window — the first moment of the next month (UTC). */
    public static Instant endExclusive(YearMonth period) {
        return period.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** True once the period has fully elapsed at {@code now} (you may only close an ended period). */
    public static boolean hasEnded(YearMonth period, Instant now) {
        return !now.isBefore(endExclusive(period)); // now >= endExclusive
    }
}
