package com.ktayl.core.finance.domain.reserving;

import java.util.List;

/**
 * The output of an actuarial reserving run (FIN-03) — the projected ultimate loss and the implied IBNR
 * (Incurred But Not Reported) reserve, per accident period and in total. Money is <b>eurocents</b> (minor
 * units); development factors are {@code double} (dimensionless ratios). IBNR = ultimate − incurred-to-date
 * (never negative here — floored at 0, since a reserve is a provision, not a receivable).
 */
public record IbnrEstimate(ReservingMethod method, List<AccidentPeriod> accidentPeriods,
        long totalLatestMinor, long totalUltimateMinor, long totalIbnrMinor) {

    /**
     * One accident period's projection.
     *
     * @param accidentPeriod            the period the losses occurred in
     * @param latestMinor               cumulative incurred-to-date (the diagonal cell)
     * @param developmentFactorToUltimate the cumulative development factor (CDF) from this period's latest lag
     *                                    to ultimate — {@code 1.0} means fully developed
     * @param ultimateMinor             projected ultimate loss
     * @param ibnrMinor                 ultimate − latest, floored at 0
     */
    public record AccidentPeriod(String accidentPeriod, long latestMinor, double developmentFactorToUltimate,
            long ultimateMinor, long ibnrMinor) {}

    public static IbnrEstimate from(ReservingMethod method, List<AccidentPeriod> periods) {
        long latest = periods.stream().mapToLong(AccidentPeriod::latestMinor).sum();
        long ultimate = periods.stream().mapToLong(AccidentPeriod::ultimateMinor).sum();
        long ibnr = periods.stream().mapToLong(AccidentPeriod::ibnrMinor).sum();
        return new IbnrEstimate(method, List.copyOf(periods), latest, ultimate, ibnr);
    }
}
