package com.ktayl.core.billing.invoicing;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure installment-schedule math — the heart of BILL-012, and the thing an interviewer probes on money:
 * split a premium (integer eurocents) into N installments that **reconcile to the cent**, and space the
 * due dates across the policy term. No I/O → fully unit-testable.
 *
 * <p>Split rule: each installment = {@code total / N}; the **last absorbs the remainder** so
 * Σ installments == total exactly (never a lost or invented cent). MVP default N=1 (single annual premium).
 */
public final class InstallmentScheduler {

    private InstallmentScheduler() {}

    /** One scheduled installment (1-based seq). */
    public record ScheduledInstallment(int seq, LocalDate dueDate, long amountMinor) {}

    /**
     * @param totalMinor gross premium in eurocents (> 0)
     * @param count      number of installments (>= 1); MVP uses 1
     * @param inception  policy effective date (first installment due here)
     * @param expiry     policy expiry (installments spaced evenly across [inception, expiry))
     */
    public static List<ScheduledInstallment> split(long totalMinor, int count, LocalDate inception, LocalDate expiry) {
        if (totalMinor <= 0) throw new IllegalArgumentException("totalMinor must be > 0");
        if (count < 1) throw new IllegalArgumentException("count must be >= 1");
        if (totalMinor < count) throw new IllegalArgumentException("premium too small to split into " + count + " installments");
        if (inception == null || expiry == null || !expiry.isAfter(inception)) {
            throw new IllegalArgumentException("expiry must be after inception");
        }

        long base = totalMinor / count;
        long remainder = totalMinor % count;                 // 0..count-1
        long stepDays = Math.max(1, ChronoUnit.DAYS.between(inception, expiry) / count);

        List<ScheduledInstallment> out = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            long amount = (k == count - 1) ? base + remainder : base;   // last absorbs the remainder
            LocalDate due = inception.plusDays((long) k * stepDays);
            out.add(new ScheduledInstallment(k + 1, due, amount));
        }
        return out;
    }
}
