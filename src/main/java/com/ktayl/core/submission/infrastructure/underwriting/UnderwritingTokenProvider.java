package com.ktayl.core.submission.infrastructure.underwriting;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * OAuth2 <b>client-credentials</b> token provider for the underwriting (#12) handoff (SUB-02 prod increment).
 * It mints + caches an access token scoped {@code underwriting:write} and hands it to
 * {@link UnderwritingHttpAdapter}, which attaches it as a {@code Bearer} header. The M2M bind mirrors the
 * underwriting→ktayl-core direction (ADR-016): a client-credentials grant against Authentik's token endpoint.
 *
 * <p><b>Enabled only when configured.</b> It is active ONLY when {@code underwriting.oauth2.client-id} is
 * non-blank. In DEV (no client-id) {@link #token()} returns {@link Optional#empty()} → the adapter calls
 * underwriting UNAUTHENTICATED (underwriting's dev API auth is OFF). This keeps the dev path working without a
 * separate bean condition: one provider, a null-check gate.
 *
 * <p><b>Caching + refresh.</b> The token + its expiry are held in a single {@link AtomicReference} (lock-free
 * reads on the hot path). A cached token is reused until it is within {@link #REFRESH_SKEW} (~60s) of expiry;
 * the refresh itself is {@code synchronized} so a stampede of concurrent callers mints exactly one new token
 * (double-checked inside the lock). On a token-endpoint failure the error surfaces to the caller — the use
 * case turns a handoff failure into a 502 (the handoff is retryable); we never fall through to an
 * unauthenticated call when auth IS configured.
 *
 * <p><b>HTTP/1.1-safe, like {@code YenteClient} / {@code UnderwritingHttpAdapter}:</b> uses
 * {@link SimpleClientHttpRequestFactory} (plain HttpURLConnection → HTTP/1.1 + Content-Length, NO h2c
 * upgrade). Authentik's token endpoint is reached cleartext in-cluster; the default JDK HttpClient would
 * default to HTTP/2 and send an h2c Upgrade that some ASGI servers drop the body for (it bit screening).
 */
@Component
public class UnderwritingTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(UnderwritingTokenProvider.class);

    /** Refresh when the cached token is within this window of expiring (clock-skew + round-trip buffer). */
    private static final Duration REFRESH_SKEW = Duration.ofSeconds(60);

    private final RestClient http;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final boolean enabled;

    private final AtomicReference<CachedToken> cache = new AtomicReference<>(null);

    @Autowired
    public UnderwritingTokenProvider(
            @Value("${underwriting.oauth2.token-uri:}") String tokenUri,
            @Value("${underwriting.oauth2.client-id:}") String clientId,
            @Value("${underwriting.oauth2.client-secret:}") String clientSecret,
            @Value("${underwriting.oauth2.scope:underwriting:write}") String scope) {
        this(tokenUri, clientId, clientSecret, scope,
                RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()));
    }

    /** Package-private seam: lets a test bind a {@code MockRestServiceServer} to the token endpoint. */
    UnderwritingTokenProvider(String tokenUri, String clientId, String clientSecret, String scope,
            RestClient.Builder httpBuilder) {
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = (scope == null || scope.isBlank()) ? "underwriting:write" : scope;
        this.enabled = clientId != null && !clientId.isBlank();
        this.http = httpBuilder.build();
        if (!enabled) {
            log.info("underwriting OAuth2 not configured (no client-id) — handoff calls stay UNAUTHENTICATED");
        }
    }

    /**
     * @return a valid access token, or {@link Optional#empty()} when OAuth2 is not configured (dev). A cached
     *     token is returned until it nears expiry, at which point a single new token is minted.
     */
    public Optional<String> token() {
        if (!enabled) {
            return Optional.empty();
        }
        CachedToken current = cache.get();
        if (current != null && current.isFresh()) {
            return Optional.of(current.accessToken());
        }
        return Optional.of(refresh());
    }

    /** Mint a new token (double-checked under the lock so a concurrent burst mints exactly one). */
    private synchronized String refresh() {
        CachedToken current = cache.get();
        if (current != null && current.isFresh()) {
            return current.accessToken();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("scope", scope);

        Map<?, ?> resp = http.post().uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(Map.class);
        if (resp == null) {
            throw new IllegalStateException("underwriting token endpoint returned no body");
        }
        Object accessToken = resp.get("access_token");
        if (accessToken == null || accessToken.toString().isBlank()) {
            throw new IllegalStateException("underwriting token endpoint returned no access_token");
        }
        long expiresIn = toLong(resp.get("expires_in"), 300L); // default 5 min if omitted
        Instant expiresAt = Instant.now().plusSeconds(expiresIn);
        CachedToken minted = new CachedToken(accessToken.toString(), expiresAt);
        cache.set(minted);
        log.debug("underwriting OAuth2 token minted, expires in {}s (scope={})", expiresIn, scope);
        return minted.accessToken();
    }

    private static long toLong(Object o, long fallback) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o != null) {
            try {
                return Long.parseLong(o.toString().trim());
            } catch (NumberFormatException ignored) {
                // fall through to the fallback
            }
        }
        return fallback;
    }

    /** An immutable cached token + its absolute expiry; fresh until within {@link #REFRESH_SKEW} of expiry. */
    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isFresh() {
            return Instant.now().isBefore(expiresAt.minus(REFRESH_SKEW));
        }
    }
}
