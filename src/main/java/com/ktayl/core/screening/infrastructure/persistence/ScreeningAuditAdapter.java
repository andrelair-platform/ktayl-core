package com.ktayl.core.screening.infrastructure.persistence;

import com.ktayl.core.screening.domain.ScreeningAuditLog;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * JPA adapter implementing the {@link ScreeningAuditLog} port (hexagonal, ADR-013). Stamps the entry with the
 * injected {@link Clock} (deterministic in tests) and INSERTs an immutable {@link ScreeningAuditEntity}
 * (append-only, LCB-FT / DORA).
 */
@Component
class ScreeningAuditAdapter implements ScreeningAuditLog {

    private final ScreeningAuditJpaRepository jpa;
    private final Clock clock;

    ScreeningAuditAdapter(ScreeningAuditJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    public void record(UUID partyId, String action, String actorId, String datasetVersion, String detail) {
        jpa.save(new ScreeningAuditEntity(partyId, action, actorId, datasetVersion, detail, Instant.now(clock)));
    }
}
