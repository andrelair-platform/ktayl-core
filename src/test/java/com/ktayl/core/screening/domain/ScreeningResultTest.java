package com.ktayl.core.screening.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * L1 — the core ALLOW/HOLD rule lives in the pure domain. No hit → ALLOW; any hit → HOLD. No Spring, no DB.
 */
class ScreeningResultTest {

    @Test
    void verdict_is_allow_when_there_are_no_hits() {
        ScreeningResult result = new ScreeningResult("20261116", List.of());

        assertThat(result.verdict()).isEqualTo(Verdict.ALLOW);
    }

    @Test
    void verdict_is_hold_when_there_is_a_hit() {
        ScreeningResult result = new ScreeningResult("20261116",
                List.of(new Hit(Bucket.SANCTION, 92, "OFAC SDN — John Doe")));

        assertThat(result.verdict()).isEqualTo(Verdict.HOLD);
    }

    @Test
    void null_hits_are_treated_as_an_empty_allow() {
        ScreeningResult result = new ScreeningResult("20261116", null);

        assertThat(result.hits()).isEmpty();
        assertThat(result.verdict()).isEqualTo(Verdict.ALLOW);
    }
}
