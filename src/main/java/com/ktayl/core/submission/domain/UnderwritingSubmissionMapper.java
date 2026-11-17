package com.ktayl.core.submission.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Maps a routed {@link Submission} + its exposure dossier onto the underwriting (#12) submission contract
 * (SUB-02) — PURE domain (hexagonal, ADR-013): zero framework / HTTP / JSON. It encodes the agreed field
 * mapping (the only place the two models meet), so the HTTP adapter stays a thin transport. All fallbacks are
 * deliberate — the underwriting contract has required, bounded, enum-constrained fields, and a submission
 * dossier may be incomplete, so a missing value maps to a safe default rather than a failed handoff.
 *
 * <p>Mapping:
 * <ul>
 *   <li>{@code counterparty.name} ← {@code insuredName}</li>
 *   <li>{@code counterparty.country} / {@code country} ← the FIRST exposure's country (the primary risk
 *       country proxies the counterparty country); {@code "XX"} if there are no exposures</li>
 *   <li>{@code line_of_business} ← {@code productLine} mapped to the UW enum; {@code commercial_property}
 *       if unknown</li>
 *   <li>{@code tiv_eur} ← SUM of all exposures' TIV in EUROCENTS (×100, rounded); {@code 1} if the sum is 0
 *       (the contract requires {@code > 0})</li>
 *   <li>{@code occupancy} ← the first exposure's free-string occupancy mapped to the UW enum; {@code office}
 *       by default</li>
 *   <li>{@code postcode} ← the first exposure's postcode; {@code "00000"} if null/blank</li>
 *   <li>{@code requested_cover} ← {@code productLine + " cover"}, clamped to 255</li>
 *   <li>{@code broker_ref} ← {@code brokerRef} truncated to 64 (nullable)</li>
 * </ul>
 */
public final class UnderwritingSubmissionMapper {

    private static final String DEFAULT_COUNTRY = "XX";
    private static final String DEFAULT_POSTCODE = "00000";
    private static final String DEFAULT_LOB = "commercial_property";
    private static final String DEFAULT_OCCUPANCY = "office";
    private static final int BROKER_REF_MAX = 64;
    private static final int REQUESTED_COVER_MAX = 255;

    private UnderwritingSubmissionMapper() {}

    public static UnderwritingSubmissionRequest toRequest(Submission submission, List<ExposureItem> exposures) {
        ExposureItem primary = (exposures == null || exposures.isEmpty()) ? null : exposures.get(0);

        String country = primary == null ? DEFAULT_COUNTRY : primary.country();
        String postcode = (primary == null || primary.postcode() == null || primary.postcode().isBlank())
                ? DEFAULT_POSTCODE : primary.postcode().strip();
        String occupancy = mapOccupancy(primary == null ? null : primary.occupancy());

        return new UnderwritingSubmissionRequest(
                submission.insuredName(),
                country,
                mapLineOfBusiness(submission.productLine()),
                sumTivEurCents(exposures),
                occupancy,
                country,
                postcode,
                clamp(submission.productLine() + " cover", REQUESTED_COVER_MAX),
                truncate(submission.brokerRef(), BROKER_REF_MAX));
    }

    /** Map the free-form product line to the UW {@code line_of_business} enum; default commercial_property. */
    private static String mapLineOfBusiness(String productLine) {
        if (productLine == null) {
            return DEFAULT_LOB;
        }
        return switch (productLine.strip().toUpperCase(Locale.ROOT)) {
            case "COMMERCIAL_PROPERTY", "PROPERTY" -> "commercial_property";
            case "MARINE" -> "marine";
            case "ENGINEERING" -> "engineering";
            case "FINANCIAL_LINES" -> "financial_lines";
            default -> DEFAULT_LOB;
        };
    }

    /** Case-insensitive contains-match of the free occupancy string onto the UW enum; default office. */
    private static String mapOccupancy(String occupancy) {
        if (occupancy == null || occupancy.isBlank()) {
            return DEFAULT_OCCUPANCY;
        }
        String o = occupancy.toLowerCase(Locale.ROOT);
        if (o.contains("fireworks")) {
            return "fireworks_manufacturing";
        }
        if (o.contains("manufacturing")) {
            return "light_manufacturing";
        }
        if (o.contains("warehouse")) {
            return "warehouse";
        }
        if (o.contains("retail")) {
            return "retail";
        }
        if (o.contains("office")) {
            return "office";
        }
        return DEFAULT_OCCUPANCY;
    }

    /** Sum every exposure's TIV to eurocents (×100, rounded to a long); 1 if the sum is 0 (contract > 0). */
    private static long sumTivEurCents(List<ExposureItem> exposures) {
        BigDecimal total = BigDecimal.ZERO;
        if (exposures != null) {
            for (ExposureItem e : exposures) {
                if (e.tivAmount() != null) {
                    total = total.add(e.tivAmount());
                }
            }
        }
        long cents = total.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return cents > 0 ? cents : 1L;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String clamp(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
