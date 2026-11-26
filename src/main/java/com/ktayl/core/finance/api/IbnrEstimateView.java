package com.ktayl.core.finance.api;

import java.util.List;

/**
 * The result of an IBNR estimation run (FIN-03) — per accident period and in total. Money is eurocents;
 * {@code developmentFactorToUltimate} is the dimensionless CDF. The published, wire-stable shape of
 * {@link com.ktayl.core.finance.domain.reserving.IbnrEstimate}.
 */
public record IbnrEstimateView(String method, String currency, long totalLatestMinor, long totalUltimateMinor,
        long totalIbnrMinor, List<PeriodView> accidentPeriods) {

    public record PeriodView(String accidentPeriod, long latestMinor, double developmentFactorToUltimate,
            long ultimateMinor, long ibnrMinor) {}
}
