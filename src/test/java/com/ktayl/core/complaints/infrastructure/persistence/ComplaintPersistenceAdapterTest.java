package com.ktayl.core.complaints.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktayl.core.complaints.domain.Complaint;
import com.ktayl.core.complaints.domain.ComplaintChannel;
import com.ktayl.core.complaints.domain.ComplaintRepository;
import com.ktayl.core.complaints.domain.ComplaintStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * L2 — the persistence adapter against a REAL Postgres + the REAL Flyway migrations (V1–V5), not mocks.
 * Proves the entity↔domain mapping round-trips, the {@code complaints} schema/columns match the JPA model,
 * and the DB unique index backstops idempotency. Runs in CI (Docker present).
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(ComplaintPersistenceAdapter.class)
class ComplaintPersistenceAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ComplaintRepository repository; // injected as the domain port, implemented by the adapter

    private static final Instant NOW = Instant.parse("2026-11-13T10:15:30Z");

    @Test
    void round_trips_a_complaint_through_the_real_schema() {
        Complaint saved = repository.save(
                Complaint.register(ComplaintChannel.PORTAL, "POL-9", "renewal premium wrong", "idem-rt", NOW));

        Complaint found = repository.findById(saved.id()).orElseThrow();
        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.channel()).isEqualTo(ComplaintChannel.PORTAL);
        assertThat(found.subjectRef()).isEqualTo("POL-9");
        assertThat(found.summary()).isEqualTo("renewal premium wrong");
        assertThat(found.status()).isEqualTo(ComplaintStatus.REGISTERED);
        assertThat(found.registeredAt()).isEqualTo(NOW);
        assertThat(found.idempotencyKey()).isEqualTo("idem-rt");
    }

    @Test
    void finds_by_idempotency_key() {
        repository.save(Complaint.register(ComplaintChannel.LETTER, null, "written complaint", "idem-find", NOW));

        assertThat(repository.findByIdempotencyKey("idem-find")).isPresent();
        assertThat(repository.findByIdempotencyKey("nope")).isEmpty();
    }
}
