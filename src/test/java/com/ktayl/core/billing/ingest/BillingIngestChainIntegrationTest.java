package com.ktayl.core.billing.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ktayl.core.billing.persistence.InstallmentEntity;
import com.ktayl.core.billing.persistence.InstallmentRepository;
import com.ktayl.core.billing.persistence.InvoiceEntity;
import com.ktayl.core.billing.persistence.InvoiceRepository;
import com.ktayl.core.billing.persistence.IngestedPolicyRepository;
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
 * L2 — the FULL bound-risk → cash thread against a REAL Postgres AND a REAL NATS JetStream, through the
 * REAL Spring wiring (not a mock, not a direct service call). It boots the whole app context so the actual
 * {@link com.ktayl.core.shared.config.NatsConfig} connection, {@link UnderwritingEventConsumer} and
 * {@link com.ktayl.core.billing.invoicing.InvoiceOnIngestListener} all engage, publishes one real event,
 * and asserts the committed {@code ingested_policy} + {@code invoice} + {@code installment} rows.
 *
 * <p><b>Why this test exists (the regression floor).</b> Three bugs reached prod (2026-11-14) because this
 * exact chain is <i>dormant on dev</i> (single-consumer discipline) and was only first exercised in prod —
 * so CI mocks and the dev QA gate could not see them. Each is now caught here, pre-merge:
 * <ul>
 *   <li><b>wrong NATS address / no connection</b> → the consumer never binds → no ingest → this fails;</li>
 *   <li><b>{@code @ConditionalOnBean} silently dropping the consumer</b> → no durable consumer → no ingest
 *       → this fails (the deterministic {@code @ConditionalOnExpression} gate is what makes it bind);</li>
 *   <li><b>{@code @TransactionalEventListener(AFTER_COMMIT)} not committing the invoice</b> → the invoice
 *       INSERT runs but is discarded → the {@code invoice}/{@code installment} assertions fail (the
 *       {@code REQUIRES_NEW} on the listener is what makes it persist).</li>
 * </ul>
 * A boundary that is only ever mocked is untested (testing.md); this is the real-collaborator L2.
 */
// MOCK (the default) not NONE: SecurityConfig's filterChain needs an HttpSecurity bean, which only exists
// with a servlet web context. We never hit HTTP here — the mock context just lets security wire.
@SpringBootTest
@Testcontainers
class BillingIngestChainIntegrationTest {

    static final String STREAM = "UNDERWRITING_EVENTS";
    static final String SUBJECT = "insurance.underwriting.bound-risk";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> nats =
            new GenericContainer<>("nats:2.10-alpine").withCommand("-js").withExposedPorts(4222);

    /**
     * Point the app at the test NATS AND create the stream BEFORE the context refreshes — the consumer
     * binds at {@code ApplicationReadyEvent} and needs the stream to already exist (as gitops provides it
     * in the cluster). {@code @DynamicPropertySource} runs after the containers start but before context
     * init, which is exactly the window we need.
     */
    @DynamicPropertySource
    static void natsProperties(DynamicPropertyRegistry registry) throws Exception {
        String url = "nats://" + nats.getHost() + ":" + nats.getMappedPort(4222);
        registry.add("billing.nats.url", () -> url);
        try (Connection c = Nats.connect(url)) {
            c.jetStreamManagement().addStream(StreamConfiguration.builder()
                    .name(STREAM)
                    .subjects("insurance.underwriting.>")
                    .storageType(StorageType.Memory)
                    .build());
        }
    }

    /** Avoid the eager OIDC issuer fetch at startup (the resource server would call out to Authentik). */
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired IngestedPolicyRepository ingested;
    @Autowired InvoiceRepository invoices;
    @Autowired InstallmentRepository installments;
    @Autowired Connection natsConnection;

    @Test
    void bound_risk_event_flows_through_the_consumer_to_a_committed_reconciling_invoice() throws Exception {
        String policy = "POL-L2-" + System.nanoTime();
        String json = """
                {"policy_number":"%s","premium_minor":240000,"currency":"EUR","product_code":"MARINE",
                 "effective_date":"2026-01-01","expiry_date":"2027-01-01","submission_id":"S","quote_id":"Q"}
                """.formatted(policy);

        // publish the REAL event into the stream (the app's durable consumer must drain it)
        natsConnection.jetStream().publish(SUBJECT, json.getBytes(StandardCharsets.UTF_8));

        // the whole async chain must land a COMMITTED invoice (this is what the 3 prod bugs each broke)
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(invoices.findByPolicyNumber(policy)).isPresent());

        assertThat(ingested.findById(policy)).isPresent();
        InvoiceEntity invoice = invoices.findByPolicyNumber(policy).orElseThrow();
        assertThat(invoice.getStatus()).isEqualTo("issued");
        assertThat(invoice.getTotalMinor()).isEqualTo(240_000L);

        var items = installments.findByInvoiceIdOrderBySeq(invoice.getId());
        assertThat(items).isNotEmpty();
        assertThat(items.stream().mapToLong(InstallmentEntity::getAmountMinor).sum())
                .as("invoice total reconciles to the sum of its installments")
                .isEqualTo(invoice.getTotalMinor());
    }
}
