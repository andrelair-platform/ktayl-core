package com.ktayl.core.billing.invoicing;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Resolves the broker commission rate for a product and splits a gross premium into net + commission
 * (BILL-012b). The rate is config-driven (basis points) — a per-{@code product_code} override map plus a
 * default — because a <b>commission schedule is a billing/distribution concern, not an underwriting one</b>:
 * the UW bound-risk event carries only the gross premium, so the split is applied here with zero UW change.
 *
 * <p>Money is integer eurocents throughout. Commission rounds <b>half-up</b> and the net absorbs the
 * complement, so {@code net + commission == gross} to the cent (a lost or invented cent is impossible).
 * Configure via {@code billing.commission.default-bps} and {@code billing.commission.rates.<PRODUCT_CODE>}.
 */
@Component
@ConfigurationProperties(prefix = "billing.commission")
public class CommissionSchedule {

    /** Fallback rate (basis points) when a product has no explicit entry. 1500 bps = 15%. */
    private int defaultBps = 1500;

    /** Per-product overrides, keyed by product_code (e.g. {@code MARINE: 1000}). */
    private Map<String, Integer> rates = Map.of();

    /** The commission rate (basis points) for a product — its override, else the default. */
    public int bpsFor(String productCode) {
        Integer override = productCode == null ? null : rates.get(productCode);
        return override != null ? override : defaultBps;
    }

    /** Split a gross premium into net + commission using the product's rate. */
    public CommissionSplit split(long grossMinor, String productCode) {
        int bps = bpsFor(productCode);
        long commission = commissionOf(grossMinor, bps);
        return new CommissionSplit(grossMinor, commission, grossMinor - commission, bps);
    }

    /** {@code round(gross * bps / 10_000)}, half-up. Uses {@link Math#multiplyExact} so an (implausible) overflow fails loud, never silently wraps into a wrong commission. */
    static long commissionOf(long grossMinor, int bps) {
        if (bps == 0) return 0L;
        return (Math.multiplyExact(grossMinor, (long) bps) + 5_000L) / 10_000L;
    }

    public int getDefaultBps() { return defaultBps; }
    public void setDefaultBps(int defaultBps) { this.defaultBps = defaultBps; }
    public Map<String, Integer> getRates() { return rates; }
    public void setRates(Map<String, Integer> rates) { this.rates = rates; }
}
