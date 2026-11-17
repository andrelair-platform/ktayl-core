package com.ktayl.core.submission.infrastructure.persistence;

import com.ktayl.core.submission.domain.TriageDecision;
import com.ktayl.core.submission.domain.TriageLog;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * JPA adapter implementing the {@link TriageLog} port (hexagonal, ADR-013). Stamps the entry with the
 * injected {@link Clock} (deterministic in tests) and INSERTs an immutable {@link SubmissionTriageEntity}.
 */
@Component
class TriageLogAdapter implements TriageLog {

    private final SubmissionTriageJpaRepository jpa;
    private final Clock clock;

    TriageLogAdapter(SubmissionTriageJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    public void record(UUID submissionId, Integer completenessScore, String appetiteFlag,
            TriageDecision decision, String reason, String actor) {
        jpa.save(new SubmissionTriageEntity(submissionId, completenessScore, appetiteFlag, decision.name(),
                reason, actor, Instant.now(clock)));
    }
}
