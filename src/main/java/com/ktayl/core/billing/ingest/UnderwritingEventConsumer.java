package com.ktayl.core.billing.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.MessageConsumer;
import io.nats.client.StreamContext;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Durable JetStream consumer of the Underwriting bound-risk event (BILL-011, ADR-003). Active only when
 * a NATS {@link Connection} bean exists (prod = authoritative; dev blank → disabled). Self-provisions its
 * durable consumer on the {@code UNDERWRITING_EVENTS} stream (created in gitops) and hands each message to
 * {@link PolicyIngestService}:
 * <ul>
 *   <li><b>ack</b> on success (ingested or idempotent-skip),</li>
 *   <li><b>term</b> a poison message (malformed / invalid — never redeliver it endlessly),</li>
 *   <li><b>nak</b> a transient failure (e.g. DB blip) → redelivered.</li>
 * </ul>
 */
@Component
@ConditionalOnBean(Connection.class)
public class UnderwritingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UnderwritingEventConsumer.class);

    private final Connection nats;
    private final ObjectMapper mapper;
    private final PolicyIngestService ingest;
    private final String stream;
    private final String subject;
    private final String durable;

    private MessageConsumer messageConsumer;

    public UnderwritingEventConsumer(
            Connection nats,
            ObjectMapper mapper,
            PolicyIngestService ingest,
            @Value("${billing.nats.stream:UNDERWRITING_EVENTS}") String stream,
            @Value("${billing.nats.subject:insurance.underwriting.bound-risk}") String subject,
            @Value("${billing.nats.durable:billing-ingest}") String durable) {
        this.nats = nats;
        this.mapper = mapper;
        this.ingest = ingest;
        this.stream = stream;
        this.subject = subject;
        this.durable = durable;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        try {
            StreamContext streamCtx = nats.getStreamContext(stream);
            ConsumerContext consumerCtx = streamCtx.createOrUpdateConsumer(ConsumerConfiguration.builder()
                    .durable(durable)
                    .filterSubject(subject)
                    .ackPolicy(AckPolicy.Explicit)
                    .build());
            this.messageConsumer = consumerCtx.consume(this::handle);
            log.info("bound-risk consumer bound to {} (subject {}, durable {})", stream, subject, durable);
        } catch (Exception e) {
            // Non-fatal: the app stays up (e.g. the stream isn't created yet). A restart re-attempts.
            log.error("bound-risk consumer failed to start (stream {} present?): {}", stream, e.getMessage());
        }
    }

    private void handle(io.nats.client.Message msg) {
        try {
            BoundRiskEvent event = mapper.readValue(msg.getData(), BoundRiskEvent.class);
            ingest.ingest(event);
            msg.ack();
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JacksonException poison) {
            log.warn("poison bound-risk message TERMed: {}", poison.getMessage());
            msg.term();
        } catch (Exception transientErr) {
            log.error("transient failure handling bound-risk message — NAK (will redeliver): {}", transientErr.getMessage());
            msg.nak();
        }
    }

    @PreDestroy
    public void stop() {
        if (messageConsumer != null) {
            try {
                messageConsumer.stop();
            } catch (Exception e) {
                log.debug("consumer stop: {}", e.getMessage());
            }
        }
    }
}
