package com.ktayl.core.claimsgl.ingest;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Durable JetStream consumer of the claim financial event (ADR-021). Active only when {@code claims.nats.url}
 * is set (prod = the authoritative consumer; dev blank → disabled — exactly one env consumes the stream, the
 * same no-double-post discipline as billing's bound-risk consumer). Self-provisions its durable consumer on
 * the {@code CLAIMS_EVENTS} stream and hands each message to {@link ClaimGlService}:
 * <ul>
 *   <li><b>ack</b> on success (recorded or idempotent-skip),</li>
 *   <li><b>term</b> a poison message (malformed / invalid — never redeliver endlessly),</li>
 *   <li><b>nak</b> a transient failure (e.g. DB blip) → redelivered.</li>
 * </ul>
 *
 * <p>Gating note (the prod lesson): uses the SAME deterministic property condition as
 * {@code NatsConfig} ({@code claims.nats.url != ''}), NOT {@code @ConditionalOnBean(Connection.class)} —
 * {@code @ConditionalOnBean} is order-dependent and can silently drop a user {@code @Component} consumer.
 */
@Component
@ConditionalOnExpression("'${claims.nats.url:}' != ''")
public class ClaimFinancialEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClaimFinancialEventConsumer.class);

    private final Connection nats;
    private final ObjectMapper mapper;
    private final ClaimGlService gl;
    private final String stream;
    private final String subject;
    private final String durable;

    private MessageConsumer messageConsumer;

    public ClaimFinancialEventConsumer(
            Connection nats,
            ObjectMapper mapper,
            ClaimGlService gl,
            @Value("${claims.nats.stream:CLAIMS_EVENTS}") String stream,
            @Value("${claims.nats.subject:insurance.claims.financial}") String subject,
            @Value("${claims.nats.durable:claimsgl-ingest}") String durable) {
        this.nats = nats;
        this.mapper = mapper;
        this.gl = gl;
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
            log.info("claim-financial consumer bound to {} (subject {}, durable {})", stream, subject, durable);
        } catch (Exception e) {
            // Non-fatal: the app stays up (e.g. the stream isn't created yet). A restart re-attempts.
            log.error("claim-financial consumer failed to start (stream {} present?): {}", stream, e.getMessage());
        }
    }

    private void handle(io.nats.client.Message msg) {
        try {
            ClaimFinancialEvent event = mapper.readValue(msg.getData(), ClaimFinancialEvent.class);
            gl.record(event);
            msg.ack();
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JacksonException poison) {
            log.warn("poison claim-financial message TERMed: {}", poison.getMessage());
            msg.term();
        } catch (Exception transientErr) {
            log.error("transient failure handling claim-financial message — NAK (will redeliver): {}", transientErr.getMessage());
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
