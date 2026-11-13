package com.ktayl.core.complaints.infrastructure.persistence;

import com.ktayl.core.complaints.application.AuditTrail;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * JPA adapter implementing the {@link AuditTrail} port (hexagonal, ADR-013). Stamps the entry with the
 * injected {@link Clock} (deterministic in tests) and INSERTs an immutable {@link ComplaintAuditEntity}.
 */
@Component
class AuditTrailAdapter implements AuditTrail {

    private final ComplaintAuditJpaRepository jpa;
    private final Clock clock;

    AuditTrailAdapter(ComplaintAuditJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    public void record(String entity, UUID entityId, String action, String actor, String detail) {
        jpa.save(new ComplaintAuditEntity(entity, entityId.toString(), action, actor, detail,
                Instant.now(clock)));
    }
}
