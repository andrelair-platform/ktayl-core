package com.ktayl.core.screening.infrastructure.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktayl.core.screening.domain.Bucket;
import com.ktayl.core.screening.domain.Hit;
import com.ktayl.core.screening.domain.Party;
import com.ktayl.core.screening.domain.ScreeningEngine;
import com.ktayl.core.screening.domain.ScreeningEngineUnavailableException;
import com.ktayl.core.screening.domain.ScreeningResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * yente / OpenSanctions adapter for the {@link ScreeningEngine} port (ADR drafted in ktayl-compliance #15 —
 * ADOPT yente over the official free sanctions lists). Matches a {@link Party} via yente's {@code /match}
 * endpoint and reads the loaded dataset version via {@code /catalog}.
 *
 * <p><b>S1 scope:</b> yente is loaded with sanctions lists, so every match → a {@link Bucket#SANCTION} hit;
 * PEP / AME / RISK_COUNTRY / UBO / ORIAS buckets are later increments. Only results scoring at/above
 * {@code screening.match-threshold} are kept.
 *
 * <p><b>Fail-closed (LCB-FT):</b> ANY HTTP/connection error throws {@link ScreeningEngineUnavailableException}
 * — the engine NEVER returns an empty (silent-ALLOW) result on an outage; the use case holds the party.
 */
@Component
public class YenteClient implements ScreeningEngine {

    private static final Logger log = LoggerFactory.getLogger(YenteClient.class);

    private final RestClient http;
    private final double threshold;
    private final ObjectMapper mapper = new ObjectMapper();

    public YenteClient(
            @Value("${screening.yente.base-url:http://yente.yente.svc:8000}") String baseUrl,
            @Value("${screening.match-threshold:0.70}") double threshold) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.threshold = threshold;
    }

    @Override
    public ScreeningResult match(Party party) {
        try {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", List.of(party.name()));
            if (party.dob() != null) {
                props.put("birthDate", List.of(party.dob().toString()));
            }
            if (party.nationality() != null && !party.nationality().isBlank()) {
                props.put("nationality", List.of(party.nationality()));
            }
            Map<String, Object> query = Map.of("schema", "Person", "properties", props);
            Map<String, Object> body = Map.of("queries", Map.of("q", query));

            // Serialize to a String so RestClient sends a Content-Length — yente's uvicorn/h11 REJECTS a
            // chunked (no-length) POST body with "Invalid HTTP request received" / "body required" (proven
            // live: the identical body via curl works, via RestClient.body(Map) streamed chunked did not).
            String json = mapper.writeValueAsString(body);
            Map<?, ?> resp = http.post().uri("/match/default")
                    .contentType(MediaType.APPLICATION_JSON).body(json)
                    .retrieve().body(Map.class);

            List<Hit> hits = parseHits(resp);
            String datasetVersion = fetchDatasetVersion();
            return new ScreeningResult(datasetVersion, hits);
        } catch (ScreeningEngineUnavailableException e) {
            throw e;
        } catch (JsonProcessingException | RuntimeException e) {
            // Fail-closed: never silent-ALLOW. Any transport/HTTP/serialize/parse error → HOLD upstream.
            throw new ScreeningEngineUnavailableException("yente match failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Hit> parseHits(Map<?, ?> resp) {
        List<Hit> hits = new ArrayList<>();
        if (resp == null || !(resp.get("responses") instanceof Map<?, ?> responses)) {
            return hits;
        }
        if (!(responses.get("q") instanceof Map<?, ?> q) || !(q.get("results") instanceof List<?> results)) {
            return hits;
        }
        for (Object o : results) {
            if (!(o instanceof Map<?, ?> result)) {
                continue;
            }
            double score = toDouble(result.get("score"));
            if (score < threshold) {
                continue;
            }
            String caption = result.get("caption") == null ? null : result.get("caption").toString();
            hits.add(new Hit(Bucket.SANCTION, (int) Math.round(score * 100), caption));
        }
        return hits;
    }

    /** GET /catalog → the {@code default} dataset's version (or the first dataset's), null if unreadable. */
    private String fetchDatasetVersion() {
        try {
            Map<?, ?> catalog = http.get().uri("/catalog").retrieve().body(Map.class);
            if (catalog == null || !(catalog.get("datasets") instanceof List<?> datasets) || datasets.isEmpty()) {
                return null;
            }
            Map<?, ?> chosen = null;
            for (Object o : datasets) {
                if (o instanceof Map<?, ?> ds && "default".equals(String.valueOf(ds.get("name")))) {
                    chosen = ds;
                    break;
                }
            }
            if (chosen == null && datasets.get(0) instanceof Map<?, ?> first) {
                chosen = first;
            }
            Object version = chosen == null ? null : chosen.get("version");
            return version == null ? null : version.toString();
        } catch (RuntimeException e) {
            log.debug("yente /catalog version read failed: {}", e.getMessage());
            return null;
        }
    }

    private static double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }
}
