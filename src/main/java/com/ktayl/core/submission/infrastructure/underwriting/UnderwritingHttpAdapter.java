package com.ktayl.core.submission.infrastructure.underwriting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktayl.core.submission.domain.UnderwritingPort;
import com.ktayl.core.submission.domain.UnderwritingSubmissionRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP adapter implementing the {@link UnderwritingPort} (hexagonal, ADR-013) — the ONLY place that knows the
 * handoff is a REST call to the underwriting (#12) service. It maps the domain
 * {@link UnderwritingSubmissionRequest} to underwriting's snake_case wire contract
 * ({@code POST /v1/submissions} → {@code 201 {"id": "<uuid>", ...}}) and returns the created UW submission id.
 *
 * <p><b>Conditional auth (SUB-02 prod).</b> When OAuth2 is configured ({@code underwriting.oauth2.client-id}
 * set — prod, where underwriting's API auth is ON), the {@link UnderwritingTokenProvider} mints a
 * client-credentials token scoped {@code underwriting:write} and this adapter attaches it as a
 * {@code Bearer} header. When it is NOT configured (dev: no client-id) the provider yields no token and the
 * call stays UNAUTHENTICATED. The M2M pattern is the same the underwriting BFF uses to call ktayl-core
 * (ADR-016), so it is a config + token-provider change, not a contract one.
 *
 * <p><b>HTTP/1.1-safe, like {@code YenteClient}:</b> uses {@link SimpleClientHttpRequestFactory} (plain
 * HttpURLConnection → HTTP/1.1 + Content-Length, NO h2c upgrade). The default JDK HttpClient defaults to
 * HTTP/2 and sends an h2c Upgrade on cleartext http:// that some ASGI servers (uvicorn/h11) reject and drop
 * the body for (it bit screening). The body is serialized to a String so a Content-Length is sent (no chunked
 * transfer). Any transport/HTTP/parse failure surfaces to the caller — the use case wraps it as an
 * {@code UnderwritingHandoffException} (502), never a silent success.
 */
@Component
public class UnderwritingHttpAdapter implements UnderwritingPort {

    private static final Logger log = LoggerFactory.getLogger(UnderwritingHttpAdapter.class);

    private final RestClient http;
    private final UnderwritingTokenProvider tokenProvider;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public UnderwritingHttpAdapter(
            @Value("${underwriting.base-url:http://underwriting.underwriting.svc:8080}") String baseUrl,
            UnderwritingTokenProvider tokenProvider) {
        this(RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()).baseUrl(baseUrl),
                tokenProvider);
    }

    /** Package-private seam: lets a test bind a {@code MockRestServiceServer} to the underwriting endpoint. */
    UnderwritingHttpAdapter(RestClient.Builder httpBuilder, UnderwritingTokenProvider tokenProvider) {
        this.http = httpBuilder.build();
        this.tokenProvider = tokenProvider;
    }

    @Override
    public String createSubmission(UnderwritingSubmissionRequest request) {
        try {
            String json = mapper.writeValueAsString(toWire(request));
            // Attach the OAuth2 client-credentials bearer when underwriting auth is configured (prod); in dev
            // the provider yields no token → the call stays unauthenticated (underwriting's dev auth is OFF).
            Map<?, ?> resp = http.post().uri("/v1/submissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> tokenProvider.token().ifPresent(h::setBearerAuth))
                    .body(json)
                    .retrieve().body(Map.class);
            String id = resp == null ? null : String.valueOf(resp.get("id"));
            if (id == null || id.isBlank() || "null".equals(id)) {
                throw new IllegalStateException("underwriting response had no submission id");
            }
            log.info("underwriting handoff created UW submission {}", id);
            return id;
        } catch (JsonProcessingException | RuntimeException e) {
            // Surface the failure — the use case turns it into a 502 (never a silent success). The submission
            // is already ROUTED locally; the handoff is retryable.
            throw new HandoffTransportException(e.getMessage(), e);
        }
    }

    /** Map the domain request to underwriting's snake_case wire body. {@code broker_ref} may be null. */
    private static Map<String, Object> toWire(UnderwritingSubmissionRequest r) {
        Map<String, Object> counterparty = new LinkedHashMap<>();
        counterparty.put("name", r.counterpartyName());
        counterparty.put("country", r.counterpartyCountry());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("counterparty", counterparty);
        body.put("line_of_business", r.lineOfBusiness());
        body.put("tiv_eur", r.tivEurCents());
        body.put("occupancy", r.occupancy());
        body.put("country", r.country());
        body.put("postcode", r.postcode());
        body.put("requested_cover", r.requestedCover());
        body.put("broker_ref", r.brokerRef());   // nullable → Jackson emits null
        return body;
    }

    /** Internal transport-failure marker; the use case maps the public domain exception. */
    static final class HandoffTransportException extends RuntimeException {
        HandoffTransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
