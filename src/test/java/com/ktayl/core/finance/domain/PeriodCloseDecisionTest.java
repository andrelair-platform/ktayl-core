package com.ktayl.core.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktayl.core.finance.domain.PeriodCloseDecision.Blocker;
import java.time.Instant;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** L1 — the pure close decision + the period-ended arithmetic (FINCLOSE-02 AC-1). */
class PeriodCloseDecisionTest {

    @Test
    void refuses_a_period_that_has_not_ended() {
        assertThat(PeriodCloseDecision.blocker(false, 0, "RECONCILED", 0, false))
                .contains(Blocker.PERIOD_NOT_ENDED);
    }

    @Test
    void refuses_an_unsettled_subledger() {
        assertThat(PeriodCloseDecision.blocker(true, 1, "RECONCILED", 0, false))
                .contains(Blocker.SUBLEDGER_NOT_SETTLED);
    }

    @Test
    void refuses_gl_drift_unless_forced() {
        assertThat(PeriodCloseDecision.blocker(true, 0, "DRIFT", 80_000, false))
                .contains(Blocker.GL_DRIFT_NOT_FORCED);
        assertThat(PeriodCloseDecision.blocker(true, 0, "DRIFT", 80_000, true)).isEmpty(); // forced → allowed
    }

    @Test
    void allows_a_settled_reconciled_or_gl_unavailable_ended_period() {
        assertThat(PeriodCloseDecision.blocker(true, 0, "RECONCILED", 0, false)).isEmpty();
        assertThat(PeriodCloseDecision.blocker(true, 0, "UNAVAILABLE", 0, false)).isEmpty();
    }

    @Test
    void period_has_ended_is_half_open_in_utc() {
        YearMonth oct = YearMonth.of(2026, 10);
        assertThat(AccountingPeriods.hasEnded(oct, Instant.parse("2026-10-31T23:59:59Z"))).isFalse();
        assertThat(AccountingPeriods.hasEnded(oct, Instant.parse("2026-11-01T00:00:00Z"))).isTrue();
    }
}
