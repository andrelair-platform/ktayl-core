package com.ktayl.core.submission.api;

import java.math.BigDecimal;

/**
 * Published input for appending an exposure item to a submission's dossier (ADR-005). A flat api-local value.
 *
 * @param siteName    the exposed site / location (required)
 * @param country     ISO country of the site (required)
 * @param tivAmount   total insured value at the site (required, positive)
 * @param currency    currency of the TIV (required)
 * @param occupancy   the site's occupancy / use (nullable)
 * @param lossHistory free-text loss history for the site (nullable)
 * @param catFlag     whether the site is geo-CAT exposed
 */
public record AddExposureCommand(String siteName, String country, BigDecimal tivAmount, String currency,
        String occupancy, String lossHistory, boolean catFlag) {
}
