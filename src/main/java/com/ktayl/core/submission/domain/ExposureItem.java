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
    // DDD factory: the args ARE the exposure-item fields (a data-carrier signature, not bloat).
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static ExposureItem create(UUID submissionId, String siteName, String country, BigDecimal tivAmount,
            String currency, String occupancy, String postcode, String lossHistory, boolean catFlag,
            Instant now) {
        // Validation order preserved (site → country → tiv → currency); requireText strips + requires.
        String site = requireText(siteName, "site name");
        String ctry = requireText(country, "country");
        if (tivAmount == null || tivAmount.signum() <= 0) {
            throw new InvalidSubmissionException("tiv amount must be positive");
        }
        String curr = requireText(currency, "currency");
        return new ExposureItem(UUID.randomUUID(), submissionId, site, ctry, tivAmount,
                curr, occupancy == null ? null : occupancy.strip(),
                postcode == null ? null : postcode.strip(), lossHistory, catFlag, now);
    }

    /** Require a non-blank text field, returning it stripped (throws with "&lt;field&gt; is required"). */
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidSubmissionException(field + " is required");
        }
        return value.strip();
    }

    /** Rebuild an exposure item from persisted state (used only by the persistence adapter). */
    @SuppressWarnings("checkstyle:ParameterNumber") // data-carrier: the args ARE the persisted fields
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
