package com.ktayl.core.shared.config;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * NATS connection — created ONLY when {@code billing.nats.url} is non-blank. PROD is the authoritative
 * ingest consumer (its URL is set); dev leaves it blank so exactly one env consumes the shared
 * UNDERWRITING_EVENTS stream (no split/double-invoice — the HR_LIFECYCLE single-consumer discipline).
 * Infinite reconnect → durable-consumer resilience across NATS blips.
 */
@Configuration
@ConditionalOnExpression("'${billing.nats.url:}' != ''")
public class NatsConfig {

    @Bean(destroyMethod = "close")
    Connection natsConnection(@Value("${billing.nats.url}") String url) throws Exception {
        Options options = new Options.Builder()
                .server(url)
                .connectionTimeout(Duration.ofSeconds(5))
                .reconnectWait(Duration.ofSeconds(2))
                .maxReconnects(-1)
                .build();
        return Nats.connect(options);
    }
}
