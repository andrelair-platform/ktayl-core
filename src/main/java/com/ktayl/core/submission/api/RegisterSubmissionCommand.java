package com.ktayl.core.submission.api;

/**
 * Published input for registering a submission (ADR-005) — a flat api-local value, no internal domain type.
 * The web adapter (and any in-process caller) maps an HTTP request onto this.
 *
 * @param brokerRef    the broker's stable reference for the submission (required)
 * @param insuredName  the insured / prospective policyholder (required)
 * @param productLine  the line of business the risk falls under (required)
 */
public record RegisterSubmissionCommand(String brokerRef, String insuredName, String productLine) {
}
