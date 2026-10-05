package com.ktayl.core.billing.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * The Underwriting <b>bound-risk</b> event (subject {@code insurance.underwriting.bound-risk}) — the
 * authoritative premium source for Billing (ADR-003). Field names + types are pinned to the REAL UW
 * payload (ktayl-underwriting {@code app/bind/service.py}): snake_case keys, {@code premium_minor} is
 * eurocents (integer), the dates are {@code YYYY-MM-DD} strings. An L3 contract test guards this shape.
 *
 * @param policyNumber  the shared key to the PAS policy (deterministic from the quote)
 * @param premiumMinor  gross premium in eurocents (minor units, never a float)
 * @param currency      ISO-4217 (EUR)
 * @param productCode   LOB / product (the PAS {@code product_code})
 * @param effectiveDate inception
 * @param expiryDate    expiry
 * @param submissionId  UW submission id (provenance; nullable)
 * @param quoteId       UW quote id (provenance; nullable)
 */
public record BoundRiskEvent(
        @JsonProperty("policy_number") String policyNumber,
        @JsonProperty("premium_minor") long premiumMinor,
        @JsonProperty("currency") String currency,
        @JsonProperty("product_code") String productCode,
        @JsonProperty("effective_date") LocalDate effectiveDate,
        @JsonProperty("expiry_date") LocalDate expiryDate,
        @JsonProperty("submission_id") String submissionId,
        @JsonProperty("quote_id") String quoteId) {}
