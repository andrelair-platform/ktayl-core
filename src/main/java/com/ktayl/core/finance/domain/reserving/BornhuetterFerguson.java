package com.ktayl.core.finance.domain.reserving;

import com.ktayl.core.finance.domain.reserving.IbnrEstimate.AccidentPeriod;
import com.ktayl.core.finance.domain.reserving.LossTriangle.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The <b>Bornhuetter-Ferguson</b> reserving method (FIN-03) — blends the chain-ladder development <i>pattern</i>
 * with an <i>a-priori</i> expected ultimate (typically premium × an expected loss ratio). It is the standard
 * choice for <b>immature</b> accident periods, where chain-ladder is unstable because a tiny observed amount is
 * multiplied by a large CDF (a small random deviation gets leveraged into a huge ultimate). BF instead reserves
 * {@code apriori × (unreported %)}, so the reserve doesn't swing with the thin early diagonal.
 *
 * <p>Per accident period, using the chain-ladder CDF from {@link ChainLadder}:
 * <pre>
 *   % reported     = 1 / CDF
 *   IBNR_BF        = apriori_ultimate × (1 − % reported)
 *   ultimate_BF    = latest (reported) + IBNR_BF
 * </pre>
 * A period with no a-priori supplied falls back to its chain-ladder ultimate (so a partial a-priori map still
 * produces a complete estimate). Money is {@code long} eurocents; the development pattern is shared with
 * chain-ladder so the two methods are directly comparable.
 */
public final class BornhuetterFerguson {

    private BornhuetterFerguson() {}

    /**
     * @param triangle               the cumulative loss triangle
     * @param aprioriUltimateMinor   expected ultimate per accident period (eurocents); a missing entry → that
     *                               period falls back to chain-ladder
     */
    public static IbnrEstimate estimate(LossTriangle triangle, Map<String, Long> aprioriUltimateMinor) {
        int maxLag = triangle.maxDevelopmentLag();
        double[] f = ChainLadder.ageToAgeFactors(triangle);
        List<AccidentPeriod> periods = new ArrayList<>();
        for (Row r : triangle.rowsOldestFirst()) {
            int latestLag = r.cells().length - 1;
            long latest = r.cells()[latestLag];
            double cdf = ChainLadder.cdfToUltimate(f, latestLag, maxLag);
            Long apriori = aprioriUltimateMinor == null ? null : aprioriUltimateMinor.get(r.accidentPeriod());

            long ultimate;
            if (apriori == null) {
                ultimate = Math.round(latest * cdf); // fall back to chain-ladder for this period
            } else {
                double percentReported = cdf == 0.0 ? 1.0 : 1.0 / cdf;
                double unreported = 1.0 - percentReported;
                long ibnrBf = Math.round(apriori * unreported);
                ultimate = latest + Math.max(0, ibnrBf);
            }
            long ibnr = Math.max(0, ultimate - latest);
            periods.add(new AccidentPeriod(r.accidentPeriod(), latest, cdf, ultimate, ibnr));
        }
        return IbnrEstimate.from(ReservingMethod.BORNHUETTER_FERGUSON, periods);
    }
}
