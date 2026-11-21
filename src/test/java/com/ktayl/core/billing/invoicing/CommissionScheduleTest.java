package com.ktayl.core.billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** L1 — commission math: the split reconciles to the cent, rounds half-up, and honours per-product overrides. */
class CommissionScheduleTest {

    private CommissionSchedule schedule(int defaultBps, Map<String, Integer> rates) {
        var s = new CommissionSchedule();
        s.setDefaultBps(defaultBps);
        s.setRates(rates);
        return s;
    }

    @Test
    void default_rate_applies_when_a_product_has_no_override() {
        var s = schedule(1500, Map.of());
        CommissionSplit split = s.split(480_000, "PROPERTY");
        assertThat(split.bps()).isEqualTo(1500);
        assertThat(split.commissionMinor()).isEqualTo(72_000); // 15% of 480_000
        assertThat(split.netMinor()).isEqualTo(408_000);
        assertThat(split.grossMinor()).isEqualTo(480_000);
    }

    @Test
    void a_product_override_beats_the_default() {
        var s = schedule(1500, Map.of("MARINE", 1000));
        assertThat(s.split(480_000, "MARINE").bps()).isEqualTo(1000);
        assertThat(s.split(480_000, "MARINE").commissionMinor()).isEqualTo(48_000); // 10%
        assertThat(s.split(480_000, "PROPERTY").bps()).isEqualTo(1500);             // still default
    }

    @Test
    void the_split_always_reconciles_to_the_cent_even_with_rounding() {
        var s = schedule(1500, Map.of());
        // 100_001 * 1500 / 10000 = 15000.15 → half-up rounds to 15000; net absorbs the rest
        CommissionSplit split = s.split(100_001, "PROPERTY");
        assertThat(split.commissionMinor()).isEqualTo(15_000);
        assertThat(split.netMinor()).isEqualTo(85_001);
        assertThat(split.commissionMinor() + split.netMinor()).isEqualTo(split.grossMinor());
    }

    @Test
    void rounds_half_up() {
        var s = schedule(5000, Map.of()); // 50%
        // 101 * 5000 / 10000 = 50.5 → half-up → 51
        assertThat(CommissionSchedule.commissionOf(101, 5000)).isEqualTo(51);
    }

    @Test
    void zero_commission_rate_yields_net_equals_gross() {
        var s = schedule(0, Map.of());
        CommissionSplit split = s.split(480_000, "PROPERTY");
        assertThat(split.commissionMinor()).isZero();
        assertThat(split.netMinor()).isEqualTo(480_000);
    }

    @Test
    void null_product_code_falls_back_to_the_default_rate() {
        var s = schedule(1500, Map.of("MARINE", 1000));
        assertThat(s.split(480_000, null).bps()).isEqualTo(1500);
    }

    @Test
    void a_non_reconciling_split_cannot_be_constructed() {
        assertThatThrownBy(() -> new CommissionSplit(100, 30, 80, 1500)) // 30 + 80 != 100
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reconcile");
    }
}
