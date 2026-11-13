package com.ktayl.core.complaints.application;

import com.ktayl.core.complaints.domain.ComplaintChannel;

/**
 * Use-case input for registering a complaint. Framework-free — the web adapter (CMPL-013) maps an HTTP
 * request + the {@code Idempotency-Key} header onto this.
 *
 * @param channel        how the complaint arrived (required)
 * @param subjectRef     the party/policy the complaint concerns, by reference (nullable)
 * @param summary        the complainant's grievance in free text (required, non-blank)
 * @param idempotencyKey client-supplied safe-retry key (nullable)
 */
public record RegisterComplaintCommand(ComplaintChannel channel, String subjectRef, String summary,
        String idempotencyKey) {
}
