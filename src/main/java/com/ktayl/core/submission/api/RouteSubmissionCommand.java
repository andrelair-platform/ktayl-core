package com.ktayl.core.submission.api;

/**
 * Published input for routing a triaged submission onward (ADR-005).
 *
 * @param target where the submission is routed (e.g. {@code "underwriting"}) — required
 */
public record RouteSubmissionCommand(String target) {
}
