package com.ktayl.core.finance.domain.reserving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.ktayl.core.finance.domain.reserving.IbnrEstimate.AccidentPeriod;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * L1 — the Chain-Ladder math, pinned to a hand-computable triangle (FIN-03). The triangle is built so each
 * accident period develops by the <i>same</i> clean factors (2.0, 1.5, 1.25), which makes the volume-weighted
 * link ratios exact and the ultimates/IBNR computable by hand:
 *
 * <pre>
 *   AP \ lag   0     1     2     3
 *   2021-A    100   200   300   375    (fully developed)
 *   2021-B    120   240   360          (→ ×1.25  = 450,  IBNR  90)
 *   2021-C    140   280                (→ ×1.875 = 525,  IBNR 245)
 *   2021-D    160                      (→ ×3.75  = 600,  IBNR 440)
 *
 *   f0 = (200+240+280)/(100+120+140) = 720/360 = 2.00
 *   f1 = (300+360)/(200+240)         = 660/440 = 1.50
 *   f2 = 375/300                     =           1.25
 *   total IBNR = 0+90+245+440 = 775   total ultimate = 1950   total latest = 1175
 * </pre>
 */
class ChainLadderTest {

    /** The clean triangle above, amounts in eurocents (the integers used directly). */
    private static LossTriangle cleanTriangle() {
        Map<String, Map<Integer, Long>> cells = new LinkedHashMap<>();
        cells.put("2021-A", Map.of(0, 100L, 1, 200L, 2, 300L, 3, 375L));
        cells.put("2021-B", Map.of(0, 120L, 1, 240L, 2, 360L));
        cells.put("2021-C", Map.of(0, 140L, 1, 280L));
        cells.put("2021-D", Map.of(0, 160L));
        return LossTriangle.of(cells);
    }

    @Test
    void volume_weighted_link_ratios_are_exact() {
        double[] f = ChainLadder.ageToAgeFactors(cleanTriangle());
        assertThat(f).hasSize(3);
        assertThat(f[0]).isCloseTo(2.00, within(1e-9));
        assertThat(f[1]).isCloseTo(1.50, within(1e-9));
        assertThat(f[2]).isCloseTo(1.25, within(1e-9));
    }

    @Test
    void projects_ultimate_and_ibnr_per_accident_period() {
        IbnrEstimate est = ChainLadder.estimate(cleanTriangle());
        Map<String, AccidentPeriod> byAp = new LinkedHashMap<>();
        est.accidentPeriods().forEach(ap -> byAp.put(ap.accidentPeriod(), ap));

        assertThat(byAp.get("2021-A").ibnrMinor()).isZero();            // fully developed
        assertThat(byAp.get("2021-A").ultimateMinor()).isEqualTo(375L);
        assertThat(byAp.get("2021-B").ultimateMinor()).isEqualTo(450L);
        assertThat(byAp.get("2021-B").ibnrMinor()).isEqualTo(90L);
        assertThat(byAp.get("2021-C").ultimateMinor()).isEqualTo(525L);
        assertThat(byAp.get("2021-C").ibnrMinor()).isEqualTo(245L);
        assertThat(byAp.get("2021-D").ultimateMinor()).isEqualTo(600L);
        assertThat(byAp.get("2021-D").ibnrMinor()).isEqualTo(440L);
    }

    @Test
    void totals_reconcile() {
        IbnrEstimate est = ChainLadder.estimate(cleanTriangle());
        assertThat(est.totalLatestMinor()).isEqualTo(1175L);
        assertThat(est.totalUltimateMinor()).isEqualTo(1950L);
        assertThat(est.totalIbnrMinor()).isEqualTo(775L);
        // ultimate == latest + ibnr, always
        assertThat(est.totalUltimateMinor()).isEqualTo(est.totalLatestMinor() + est.totalIbnrMinor());
    }

    @Test
    void the_newest_period_carries_the_largest_development_factor() {
        IbnrEstimate est = ChainLadder.estimate(cleanTriangle());
        double dCdf = est.accidentPeriods().stream().filter(a -> a.accidentPeriod().equals("2021-D"))
                .findFirst().orElseThrow().developmentFactorToUltimate();
        assertThat(dCdf).isCloseTo(3.75, within(1e-9)); // 2.0 × 1.5 × 1.25
    }

    @Test
    void a_single_fully_developed_row_has_zero_ibnr() {
        LossTriangle one = LossTriangle.of(Map.of("2020-A", Map.of(0, 500L)));
        IbnrEstimate est = ChainLadder.estimate(one);
        assertThat(est.totalIbnrMinor()).isZero();
        assertThat(est.totalUltimateMinor()).isEqualTo(500L);
    }
}
