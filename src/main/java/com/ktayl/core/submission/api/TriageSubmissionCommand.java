package com.ktayl.core.submission.api;

/**
 * Published input for triaging a submission (ADR-005). {@code decision} is a String (ROUTE / DECLINE /
 * RETURN) parsed by the implementation — an unknown value is rejected as invalid input (→ 4xx), keeping the
 * domain enum off the boundary.
 *
 * @param decision          the triage outcome: ROUTE / DECLINE / RETURN (required)
 * @param completenessScore the dossier completeness score 0–100 (nullable)
 * @param appetiteFlag      the appetite verdict marker (nullable)
 * @param reason            the triage rationale (nullable)
 */
public record TriageSubmissionCommand(String decision, Integer completenessScore, String appetiteFlag,
        String reason) {
}
