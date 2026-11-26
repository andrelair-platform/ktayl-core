package com.ktayl.core.finance.domain.reserving;

import com.ktayl.core.finance.domain.reserving.IbnrEstimate.AccidentPeriod;
import com.ktayl.core.finance.domain.reserving.LossTriangle.Row;
import java.util.ArrayList;
import java.util.List;

/**
 * The <b>Chain-Ladder</b> (link-ratio) reserving method (FIN-03) — the actuarial workhorse for IBNR. Pure
 * function of a {@link LossTriangle}; no state, no IO.
 *
 * <p>Steps (textbook):
 * <ol>
 *   <li><b>Age-to-age (link) factors</b> {@code f[d]} for each development step {@code d → d+1}, computed
 *       <i>volume-weighted</i> (all-years): {@code f[d] = Σ C[i][d+1] / Σ C[i][d]} over every accident period
 *       {@code i} that has an observed cell at lag {@code d+1}. (Volume-weighting is the standard estimator —
 *       it weights each year by its size, unlike a simple average of ratios.)</li>
 *   <li><b>Cumulative development factor to ultimate</b> (CDF) for an accident period whose latest observed lag
 *       is {@code L}: {@code CDF = Π f[d]} for {@code d = L..maxLag−1} (a fully-developed row → empty product →
 *       {@code 1.0}).</li>
 *   <li><b>Ultimate</b> {@code = latest × CDF}; <b>IBNR</b> {@code = max(0, ultimate − latest)}.</li>
 * </ol>
 *
 * <p>Money stays {@code long} eurocents; the ratios are {@code double}; the final ultimate is rounded once
 * ({@link Math#round}) back to eurocents.
 */
public final class ChainLadder {

    private ChainLadder() {}

    /**
     * Volume-weighted age-to-age factors, indexed by development step: {@code f[d]} develops lag {@code d → d+1}.
     * Length = {@code maxDevelopmentLag}. A step with no observed pair (shouldn't happen for a valid staircase
     * with ≥2 rows) defaults to {@code 1.0} (no development).
     */
    public static double[] ageToAgeFactors(LossTriangle triangle) {
        int maxLag = triangle.maxDevelopmentLag();
        List<Row> rows = triangle.rowsOldestFirst();
        double[] f = new double[maxLag];
        for (int d = 0; d < maxLag; d++) {
            long num = 0;
            long den = 0;
            for (Row r : rows) {
                if (r.cells().length > d + 1) { // this row has both lag d and lag d+1
                    den += r.cells()[d];
                    num += r.cells()[d + 1];
                }
            }
            f[d] = den == 0 ? 1.0 : (double) num / (double) den;
        }
        return f;
    }

    /** The cumulative development factor from {@code latestLag} to ultimate (product of the remaining steps). */
    static double cdfToUltimate(double[] factors, int latestLag, int maxLag) {
        double cdf = 1.0;
        for (int d = latestLag; d < maxLag; d++) {
            cdf *= factors[d];
        }
        return cdf;
    }

    /** Run the chain-ladder projection over the whole triangle, oldest accident period first. */
    public static IbnrEstimate estimate(LossTriangle triangle) {
        int maxLag = triangle.maxDevelopmentLag();
        double[] f = ageToAgeFactors(triangle);
        List<AccidentPeriod> periods = new ArrayList<>();
        for (Row r : triangle.rowsOldestFirst()) {
            int latestLag = r.cells().length - 1;
            long latest = r.cells()[latestLag];
            double cdf = cdfToUltimate(f, latestLag, maxLag);
            long ultimate = Math.round(latest * cdf);
            long ibnr = Math.max(0, ultimate - latest);
            periods.add(new AccidentPeriod(r.accidentPeriod(), latest, cdf, ultimate, ibnr));
        }
        return IbnrEstimate.from(ReservingMethod.CHAIN_LADDER, periods);
    }
}
