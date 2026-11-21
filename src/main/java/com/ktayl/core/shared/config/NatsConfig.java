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
 * Shared NATS connection — created when EITHER a module's NATS URL is configured ({@code billing.nats.url}
 * for the bound-risk ingest, or {@code claims.nats.url} for the claims-GL ingest; ADR-021). Both point at the
 * same NATS server, so one Connection serves every durable consumer. PROD is the authoritative consumer env
 * (its URLs are set); dev leaves them blank so exactly one env consumes each shared stream (no split/
 * double-post — the single-consumer discipline). The URL is whichever of the two is non-blank
 * ({@code billing.nats.url} wins if both are set — they point at the same server). Infinite reconnect →
 * durable-consumer resilience across NATS blips.
 *
 * <p><b>Why both URLs are injected and chosen in code</b> (not a nested-placeholder default): both are
 * declared present-but-empty ({@code ${BILLING_NATS_URL:}} / {@code ${CLAIMS_NATS_URL:}}), and Spring's
 * {@code ${a:b}} default only fires when {@code a} is <i>absent</i>, not when it's empty — so
 * {@code ${billing.nats.url:${claims.nats.url:}}} would resolve to {@code ""} and NATS would default to
 * {@code localhost:4222}. The L2 ingest-chain test caught exactly this.
 */
@Configuration
@ConditionalOnExpression("'${billing.nats.url:}' != '' or '${claims.nats.url:}' != ''")
public class NatsConfig {

    @Bean(destroyMethod = "close")
    Connection natsConnection(
            @Value("${billing.nats.url:}") String billingUrl,
            @Value("${claims.nats.url:}") String claimsUrl) throws Exception {
        String url = !billingUrl.isBlank() ? billingUrl : claimsUrl;
        Options options = new Options.Builder()
                .server(url)
                .connectionTimeout(Duration.ofSeconds(5))
                .reconnectWait(Duration.ofSeconds(2))
                .maxReconnects(-1)
                .build();
        return Nats.connect(options);
    }
}
