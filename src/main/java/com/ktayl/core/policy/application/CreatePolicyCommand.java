package com.ktayl.core.policy.application;

import java.time.LocalDate;

/** The thin PAS create command (ADR-014 Phase 1 — bind path; no premium/limits/terms yet). */
public record CreatePolicyCommand(String policyNumber, String holderName, String productCode,
        LocalDate effectiveDate, LocalDate expiryDate) {}
