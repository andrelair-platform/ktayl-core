package com.ktayl.core.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * L1 — the period-close money arithmetic (FINCLOSE-01 AC-7). Pure, minor units, no float.
 */
class CloseArithmeticTest {

    @Test
    void drift_is_subledger_minus_gl() {
        assertThat(CloseArithmetic.drift(780_000L, 780_000L)).isZero();        // reconciled
        assertThat(CloseArithmetic.drift(780_000L, 700_000L)).isEqualTo(80_000L); // GL short
        assertThat(CloseArithmetic.drift(700_000L, 780_000L)).isEqualTo(-80_000L); // GL over
    }

    @Test
    void outstanding_is_invoiced_minus_received_and_may_be_negative() {
        assertThat(CloseArithmetic.outstanding(600_000L, 420_000L)).isEqualTo(180_000L);
        assertThat(CloseArithmetic.outstanding(600_000L, 600_000L)).isZero();
        // a period can collect more than it invoices (cash for a prior period's invoice) → negative
        assertThat(CloseArithmetic.outstanding(100_000L, 250_000L)).isEqualTo(-150_000L);
    }
}
