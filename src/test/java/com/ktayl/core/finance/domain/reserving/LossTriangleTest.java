package com.ktayl.core.finance.domain.reserving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * L1 — the loss-triangle shape invariant (FIN-03). A triangle must be an upper-left cumulative staircase; this
 * is the gate that stops a malformed/ragged triangle from ever reaching the calculators (it maps to a 422, not
 * a 500). Money is eurocents.
 */
class LossTriangleTest {

    @Test
    void accepts_a_valid_upper_left_staircase() {
        LossTriangle t = LossTriangle.of(Map.of(
                "2024-Q1", Map.of(0, 100L, 1, 150L),
                "2024-Q2", Map.of(0, 120L)));
        assertThat(t.accidentPeriodCount()).isEqualTo(2);
        assertThat(t.maxDevelopmentLag()).isEqualTo(1);
        assertThat(t.latestCumulativeMinor("2024-Q1")).isEqualTo(150L);
        assertThat(t.latestCumulativeMinor("2024-Q2")).isEqualTo(120L);
    }

    @Test
    void rejects_an_empty_triangle() {
        assertThatThrownBy(() -> LossTriangle.of(Map.of()))
                .isInstanceOf(TriangleShapeException.class).hasMessageContaining("empty");
    }

    @Test
    void rejects_a_ragged_triangle_where_the_newest_period_has_too_many_cells() {
        // newest (2024-Q2) must have exactly 1 cell in a 2-row triangle; here it has 2 → not a staircase
        assertThatThrownBy(() -> LossTriangle.of(Map.of(
                "2024-Q1", Map.of(0, 100L, 1, 150L),
                "2024-Q2", Map.of(0, 120L, 1, 180L))))
                .isInstanceOf(TriangleShapeException.class).hasMessageContaining("staircase");
    }

    @Test
    void rejects_a_missing_development_lag() {
        // 2024-Q1 should have lags 0 and 1, but supplies 0 and 2 (gap at 1)
        assertThatThrownBy(() -> LossTriangle.of(Map.of(
                "2024-Q1", Map.of(0, 100L, 2, 150L),
                "2024-Q2", Map.of(0, 120L))))
                .isInstanceOf(TriangleShapeException.class).hasMessageContaining("lag");
    }

    @Test
    void rejects_a_cumulative_that_falls() {
        // cumulative must be non-decreasing; 150 → 140 is impossible for a paid/incurred triangle
        assertThatThrownBy(() -> LossTriangle.of(Map.of(
                "2024-Q1", Map.of(0, 150L, 1, 140L),
                "2024-Q2", Map.of(0, 120L))))
                .isInstanceOf(TriangleShapeException.class).hasMessageContaining("non-decreasing");
    }
}
