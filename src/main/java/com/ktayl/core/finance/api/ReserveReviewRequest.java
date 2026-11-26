package com.ktayl.core.finance.api;

import java.time.LocalDate;
import java.util.Map;

/**
 * Submit a reserve review for four-eyes approval (FIN-03). Runs the chosen method over the stored triangle
 * {@code (portfolio, asOf, basis)} and persists the resulting IBNR as a PENDING review for an independent
 * checker.
 *
 * @param method               {@code CHAIN_LADDER} or {@code BORNHUETTER_FERGUSON}
 * @param aprioriUltimateMinor  expected ultimate per accident period (eurocents) — required only for
 *                              Bornhuetter-Ferguson; ignored for chain-ladder (may be null)
 */
public record ReserveReviewRequest(String portfolio, LocalDate asOf, String basis, String method,
        Map<String, Long> aprioriUltimateMinor) {}
