package com.ktayl.core.submission.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One exposed site/location in a submission's exposure dossier (sites / values / loss-history / geo-CAT) —
 * PURE business logic (hexagonal, ADR-013): zero framework / persistence / web imports. Created via
 * {@link #create} (enforcing the exposure invariants) and reconstituted via {@link #rehydrate} by the
 * persistence adapter only.
 */
public final class ExposureItem {

    private final UUID id;
    private final UUID submissionId;
    private final String siteName;
    private final String country;
    private final BigDecimal tivAmount;   // total insured value at the site
    private final String currency;
    private final String occupancy;       // nullable
    private final String postcode;        // nullable — the site's postcode (completes the UW handoff dossier)
    private final String lossHistory;     // nullable, free text
    private final boolean catFlag;        // geo-CAT exposed
    private final Instant createdAt;

    private ExposureItem(UUID id, UUID submissionId, String siteName, String country, BigDecimal tivAmount,
            String currency, String occupancy, String postcode, String lossHistory, boolean catFlag,
            Instant createdAt) {
        this.id = id;
        this.submissionId = submissionId;
        this.siteName = siteName;
        this.country = country;
        this.tivAmount = tivAmount;
        this.currency = currency;
        this.occupancy = occupancy;
        this.postcode = postcode;
        this.lossHistory = lossHistory;
        this.catFlag = catFlag;
        this.createdAt = createdAt;
    }

    /**
     * Create a new exposure item for a submission. Enforces the exposure invariants (a site name, a country,
     * a positive TIV, a currency).
     *
     * @throws InvalidSubmissionException if the site name/country/currency is blank or the TIV is null/≤ 0
     */
    public static ExposureItem create(UUID submissionId, String siteName, String country, BigDecimal tivAmount,
            String currency, String occupancy, String postcode, String lossHistory, boolean catFlag,
            Instant now) {
        if (siteName == null || siteName.isBlank()) {
            throw new InvalidSubmissionException("site name is required");
        }
        if (country == null || country.isBlank()) {
            throw new InvalidSubmissionException("country is required");
        }
        if (tivAmount == null || tivAmount.signum() <= 0) {
            throw new InvalidSubmissionException("tiv amount must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new InvalidSubmissionException("currency is required");
        }
        return new ExposureItem(UUID.randomUUID(), submissionId, siteName.strip(), country.strip(), tivAmount,
                currency.strip(), occupancy == null ? null : occupancy.strip(),
                postcode == null ? null : postcode.strip(), lossHistory, catFlag, now);
    }

    /** Rebuild an exposure item from persisted state (used only by the persistence adapter). */
    public static ExposureItem rehydrate(UUID id, UUID submissionId, String siteName, String country,
            BigDecimal tivAmount, String currency, String occupancy, String postcode, String lossHistory,
            boolean catFlag, Instant createdAt) {
        return new ExposureItem(id, submissionId, siteName, country, tivAmount, currency, occupancy, postcode,
                lossHistory, catFlag, createdAt);
    }

    public UUID id() { return id; }
    public UUID submissionId() { return submissionId; }
    public String siteName() { return siteName; }
    public String country() { return country; }
    public BigDecimal tivAmount() { return tivAmount; }
    public String currency() { return currency; }
    public String occupancy() { return occupancy; }
    public String postcode() { return postcode; }
    public String lossHistory() { return lossHistory; }
    public boolean catFlag() { return catFlag; }
    public Instant createdAt() { return createdAt; }
}
