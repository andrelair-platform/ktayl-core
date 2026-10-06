package com.ktayl.core.billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktayl.core.billing.invoicing.InstallmentScheduler.ScheduledInstallment;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** L1 — the money-math: reconciliation to the cent, rounding (last absorbs), due-date spacing, guards. */
class InstallmentSchedulerTest {

    private static final LocalDate INCEPTION = LocalDate.of(2026, 1, 1);
    private static final LocalDate EXPIRY = LocalDate.of(2027, 1, 1);

    @Test
    void single_installment_is_the_whole_premium_due_at_inception() {
        List<ScheduledInstallment> s = InstallmentScheduler.split(120_000L, 1, INCEPTION, EXPIRY);
        assertThat(s).singleElement().satisfies(i -> {
            assertThat(i.seq()).isEqualTo(1);
            assertThat(i.amountMinor()).isEqualTo(120_000L);
            assertThat(i.dueDate()).isEqualTo(INCEPTION);
        });
    }

    @Test
    void n_installments_reconcile_to_the_cent_with_the_last_absorbing_the_remainder() {
        // 100_000 / 3 = 33_333 r1 → 33_333, 33_333, 33_334
        List<ScheduledInstallment> s = InstallmentScheduler.split(100_000L, 3, INCEPTION, EXPIRY);
        assertThat(s).hasSize(3);
        assertThat(s.stream().mapToLong(ScheduledInstallment::amountMinor).sum()).isEqualTo(100_000L); // reconciles
        assertThat(s.get(0).amountMinor()).isEqualTo(33_333L);
        assertThat(s.get(1).amountMinor()).isEqualTo(33_333L);
        assertThat(s.get(2).amountMinor()).isEqualTo(33_334L); // last absorbs the +1
    }

    @Test
    void reconciles_for_many_awkward_premium_and_count_combinations() {
        long[] premiums = {1, 7, 99, 100_000, 123_457, 999_983};
        int[] counts = {1, 2, 3, 4, 12};
        for (long premium : premiums) {
            for (int count : counts) {
                if (premium < count) continue; // guarded separately
                long sum = InstallmentScheduler.split(premium, count, INCEPTION, EXPIRY)
                        .stream().mapToLong(ScheduledInstallment::amountMinor).sum();
                assertThat(sum).as("premium=%d count=%d", premium, count).isEqualTo(premium);
            }
        }
    }

    @Test
    void due_dates_are_spaced_across_the_term_and_ascending() {
        List<ScheduledInstallment> s = InstallmentScheduler.split(120_000L, 4, INCEPTION, EXPIRY);
        assertThat(s.get(0).dueDate()).isEqualTo(INCEPTION);
        for (int k = 1; k < s.size(); k++) {
            assertThat(s.get(k).dueDate()).isAfter(s.get(k - 1).dueDate());
        }
    }

    @Test
    void rejects_invalid_inputs() {
        assertThatThrownBy(() -> InstallmentScheduler.split(0, 1, INCEPTION, EXPIRY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallmentScheduler.split(100, 0, INCEPTION, EXPIRY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallmentScheduler.split(2, 3, INCEPTION, EXPIRY)).isInstanceOf(IllegalArgumentException.class); // premium < count
        assertThatThrownBy(() -> InstallmentScheduler.split(100, 1, EXPIRY, INCEPTION)).isInstanceOf(IllegalArgumentException.class); // expiry !after inception
    }
}
