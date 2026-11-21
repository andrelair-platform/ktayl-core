package com.ktayl.core.claimsgl.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ktayl.core.claimsgl.ledger.ClaimJournalEntryFactory;
import com.ktayl.core.claimsgl.persistence.ClaimLedgerOutboxEntity;
import com.ktayl.core.claimsgl.persistence.ClaimLedgerOutboxRepository;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * L2 — the FULL claim-financial-event → GL-outbox thread against a REAL Postgres AND a REAL NATS JetStream,
 * through the REAL Spring wiring (not a mock, not a direct service call). It boots the whole app context so
 * the actual {@link com.ktayl.core.shared.config.NatsConfig} connection and
 * {@link ClaimFinancialEventConsumer} engage, publishes one real event, and asserts the committed
 * {@code claim_ledger_outbox} row.
 *
 * <p><b>Why this test exists (the regression floor, ADR-021).</b> This chain is <i>dormant on dev</i>
 * (single-consumer discipline; {@code claims.nats.url} blank), so it is first exercised in prod — exactly the
 * shape that let three bugs reach prod for billing's equivalent (wrong NATS address, a {@code
 * @ConditionalOnBean}-dropped consumer, and an uncommitted outbox write). Each is caught here pre-merge: if
 * the consumer doesn't bind, or the write doesn't commit, the outbox assertion fails. A boundary that is only
 * ever mocked is untested (testing.md) — this is the real-collaborator L2.
 */
@SpringBootTest
@Testcontainers
class ClaimGlIngestChainIntegrationTest {

    static final String STREAM = "CLAIMS_EVENTS";
    static final String SUBJECT = "insurance.claims.financial";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> nats =
            new GenericContainer<>("nats:2.10-alpine").withCommand("-js").withExposedPorts(4222);

    /**
     * Point the app at the test NATS AND create the stream BEFORE the context refreshes — the consumer binds
     * at {@code ApplicationReadyEvent} and needs the stream to already exist (as gitops provides it in the
     * cluster). Setting {@code claims.nats.url} also activates the shared NATS Connection (NatsConfig now
     * gates on billing OR claims URL).
     */
    @DynamicPropertySource
    static void natsProperties(DynamicPropertyRegistry registry) throws Exception {
        String url = "nats://" + nats.getHost() + ":" + nats.getMappedPort(4222);
        registry.add("claims.nats.url", () -> url);
        try (Connection c = Nats.connect(url)) {
            c.jetStreamManagement().addStream(StreamConfiguration.builder()
                    .name(STREAM)
                    .subjects("insurance.claims.>")
                    .storageType(StorageType.Memory)
                    .build());
        }
    }

    /** Avoid the eager OIDC issuer fetch at startup (the resource server would call out to Authentik). */
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired ClaimLedgerOutboxRepository outbox;
    @Autowired Connection natsConnection;

    @Test
    void claim_financial_event_flows_through_the_consumer_to_a_committed_outbox_row() throws Exception {
        String eventId = "evt-L2-" + System.nanoTime();
        String json = """
                {"event_id":"%s","type":"reserve-set","claim_number":"CLM-L2","policy_number":"POL-L2",
                 "amount_minor":500000,"currency":"EUR","occurred_on":"2026-03-01"}
                """.formatted(eventId);

        // publish the REAL event into the stream (the app's durable consumer must drain it)
        natsConnection.jetStream().publish(SUBJECT, json.getBytes(StandardCharsets.UTF_8));

        // the async chain must land a COMMITTED outbox row (the dormant-on-dev chain bugs each broke this)
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(outbox.existsByReference(eventId)).isTrue());

        ClaimLedgerOutboxEntity row = outbox.findAll().stream()
                .filter(r -> eventId.equals(r.getReference())).findFirst().orElseThrow();
        assertThat(row.getEventType()).isEqualTo(ClaimJournalEntryFactory.RESERVE_MOVEMENT);
        assertThat(row.getAmountMinor()).isEqualTo(500_000L);
        assertThat(row.getClaimNumber()).isEqualTo("CLM-L2");
        assertThat(row.getStatus()).isEqualTo("pending"); // no ERPNext configured in the test → stays pending
    }
}
