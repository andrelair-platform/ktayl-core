package com.ktayl.core.submission.domain;

/**
 * The domain-shaped request to create an underwriting submission (SUB-02) — PURE domain (hexagonal, ADR-013):
 * zero framework / HTTP / JSON concern. Built by {@link UnderwritingSubmissionMapper} from the submission +
 * its exposure dossier and consumed by the {@link UnderwritingPort} adapter, which maps it to the underwriting
 * service's snake_case wire contract. Fields mirror that contract's shape but carry no wire detail:
 *
 * @param counterpartyName    the insured's name (underwriting {@code counterparty.name})
 * @param counterpartyCountry ISO-3166 alpha-2 country of the counterparty (first exposure's country proxy)
 * @param lineOfBusiness      the underwriting LOB enum value (already mapped, lowercase)
 * @param tivEurCents         total insured value across the dossier, in EUROCENTS (&gt; 0)
 * @param occupancy           the underwriting occupancy enum value (already mapped)
 * @param country             ISO-3166 alpha-2 country of the primary risk (first exposure)
 * @param postcode            postcode of the primary risk (first exposure, or a fallback)
 * @param requestedCover      a short requested-cover description (≤ 255)
 * @param brokerRef           the broker reference, truncated to ≤ 64 (nullable)
 */
public record UnderwritingSubmissionRequest(
        String counterpartyName,
        String counterpartyCountry,
        String lineOfBusiness,
        long tivEurCents,
        String occupancy,
        String country,
        String postcode,
        String requestedCover,
        String brokerRef) {
}
