package com.ktayl.core.complaints.application;

import java.util.UUID;

/**
 * Result of {@link RegisterComplaint}. {@code created} is false when an idempotent retry returned the
 * already-registered complaint (lets the web adapter answer 201 vs 200 — CMPL-013).
 */
public record ComplaintRegistration(UUID complaintId, boolean created) {
}
