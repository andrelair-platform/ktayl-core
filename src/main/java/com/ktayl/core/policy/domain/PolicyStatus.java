package com.ktayl.core.policy.domain;

/**
 * The PAS policy lifecycle, carried forward from ktayl-policy-service (ADR-014 fold; source: its
 * architecture doc BF-POL-02). The state machine:
 * <pre>
 *   DRAFT ──submit──▶ SUBMITTED ──activate──▶ ACTIVE ──amend──▶ AMENDED ──confirm──▶ ACTIVE
 *                         │                      │
 *                     reject│                cancel│ (reason)     ACTIVE ──expire(batch)──▶ EXPIRED
 *                         ▼                      ▼
 *                     REJECTED               CANCELLED
 * </pre>
 * Underwriting's bind drives {@code DRAFT → SUBMITTED → ACTIVE}.
 */
public enum PolicyStatus {
    DRAFT,
    SUBMITTED,
    ACTIVE,
    AMENDED,
    CANCELLED,
    EXPIRED,
    REJECTED
}
