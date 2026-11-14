package com.ktayl.core.billing.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** L1 — dunning progression: attempts increment, cross to lapse_pending once at the threshold, then resolve. */
class DunningCaseTest {

    private DunningCaseEntity newCase() {
        return new DunningCaseEntity(UUID.randomUUID(), UUID.randomUUID(), "POL-1", Instant.now());
    }

    @Test
    void crosses_to_lapse_pending_exactly_at_the_threshold_once() {
        var d = newCase();
        Instant t = Instant.now();

        assertThat(d.recordFailure("nsf", 3, t)).isFalse();      // attempt 1
        assertThat(d.getStatus()).isEqualTo(DunningCaseEntity.OPEN);
        assertThat(d.recordFailure("nsf", 3, t)).isFalse();      // attempt 2
        assertThat(d.getStatus()).isEqualTo(DunningCaseEntity.OPEN);

        assertThat(d.recordFailure("nsf", 3, t)).isTrue();       // attempt 3 → crosses
        assertThat(d.getStatus()).isEqualTo(DunningCaseEntity.LAPSE_PENDING);
        assertThat(d.getAttempts()).isEqualTo(3);

        assertThat(d.recordFailure("nsf", 3, t)).isFalse();      // attempt 4 → does NOT re-cross
        assertThat(d.getAttempts()).isEqualTo(4);
        assertThat(d.getStatus()).isEqualTo(DunningCaseEntity.LAPSE_PENDING);
    }

    @Test
    void resolve_closes_the_case() {
        var d = newCase();
        d.recordFailure("nsf", 3, Instant.now());
        d.resolve(Instant.now());
        assertThat(d.getStatus()).isEqualTo(DunningCaseEntity.RESOLVED);
    }
}
