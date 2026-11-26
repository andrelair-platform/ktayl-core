package com.ktayl.core.finance.domain.reserving;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktayl.core.finance.domain.reserving.IbnrEstimate.AccidentPeriod;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * L1 — the Bornhuetter-Ferguson math (FIN-03), on the same clean triangle as {@link ChainLadderTest}.
 * Two properties pin it: (1) BF fed an a-priori equal to the chain-ladder ultimate reproduces chain-ladder
 * exactly (the classic identity — BF only diverges when the a-priori disagrees with the pattern); (2) a
 * richer a-priori on the <i>immature</i> newest period lifts its IBNR above chain-ladder, which is the whole
 * point of BF for green years.
 */
class BornhuetterFergusonTest {

    private static LossTriangle cleanTriangle() {
        Map<String, Map<Integer, Long>> cells = new LinkedHashMap<>();
        cells.put("2021-A", Map.of(0, 100L, 1, 200L, 2, 300L, 3, 375L));
        cells.put("2021-B", Map.of(0, 120L, 1, 240L, 2, 360L));
        cells.put("2021-C", Map.of(0, 140L, 1, 280L));
        cells.put("2021-D", Map.of(0, 160L));
        return LossTriangle.of(cells);
    }

    private static Map<String, AccidentPeriod> byAp(IbnrEstimate est) {
        Map<String, AccidentPeriod> m = new LinkedHashMap<>();
        est.accidentPeriods().forEach(ap -> m.put(ap.accidentPeriod(), ap));
        return m;
    }

    @Test
    void bf_with_apriori_equal_to_chain_ladder_ultimate_reproduces_chain_ladder() {
        // a-priori = the chain-ladder ultimates → BF collapses onto chain-ladder (the textbook identity)
        Map<String, Long> apriori = Map.of("2021-A", 375L, "2021-B", 450L, "2021-C", 525L, "2021-D", 600L);
        IbnrEstimate bf = BornhuetterFerguson.estimate(cleanTriangle(), apriori);
        IbnrEstimate cl = ChainLadder.estimate(cleanTriangle());

        assertThat(bf.totalIbnrMinor()).isEqualTo(cl.totalIbnrMinor()).isEqualTo(775L);
        assertThat(bf.totalUltimateMinor()).isEqualTo(cl.totalUltimateMinor()).isEqualTo(1950L);
    }

    @Test
    void a_richer_apriori_on_the_immature_year_lifts_its_ibnr_above_chain_ladder() {
        // 2021-D is the greenest year (only lag 0 seen, CDF 3.75). A higher a-priori (800 vs CL's 600 ultimate)
        // → IBNR = 800 × (1 − 1/3.75) = 800 × 0.73333 = 587, vs chain-ladder's 440.
        Map<String, Long> apriori = Map.of("2021-D", 800L);
        AccidentPeriod d = byAp(BornhuetterFerguson.estimate(cleanTriangle(), apriori)).get("2021-D");

        assertThat(d.ibnrMinor()).isEqualTo(587L);
        assertThat(d.ultimateMinor()).isEqualTo(747L); // 160 reported + 587
        assertThat(d.ibnrMinor()).isGreaterThan(440L); // > chain-ladder for the same period
    }

    @Test
    void a_period_without_an_apriori_falls_back_to_chain_ladder() {
        // only 2021-D has an a-priori; the others must still produce their chain-ladder result
        IbnrEstimate bf = BornhuetterFerguson.estimate(cleanTriangle(), Map.of("2021-D", 800L));
        AccidentPeriod b = byAp(bf).get("2021-B");
        assertThat(b.ultimateMinor()).isEqualTo(450L); // == chain-ladder
        assertThat(b.ibnrMinor()).isEqualTo(90L);
    }
}
