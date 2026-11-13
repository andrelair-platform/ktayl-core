package com.ktayl.core.complaints.application;

import java.util.UUID;

/**
 * OUTBOUND PORT — the append-only audit log for the complaints module (per-schema audit, ADR-005/010).
 * An interface so the use case records an audit entry without knowing it is a JPA row; the adapter lives in
 * {@code infrastructure.persistence}.
 */
public interface AuditTrail {

    /** Append one immutable audit entry. {@code actor} is the acting principal (CMPL-011: a seam for the OIDC subject). */
    void record(String entity, UUID entityId, String action, String actor, String detail);
}
