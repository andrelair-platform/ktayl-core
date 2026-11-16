package com.ktayl.core.screening.infrastructure.web;

import com.ktayl.core.screening.api.PartyRef;
import com.ktayl.core.screening.api.ScreeningGate;
import com.ktayl.core.screening.api.ScreeningOutcome;
import com.ktayl.core.screening.application.DecideAlert;
import com.ktayl.core.screening.domain.AlertView;
import com.ktayl.core.screening.domain.Bucket;
import com.ktayl.core.screening.domain.CaseStatus;
import com.ktayl.core.screening.domain.Risk;
import com.ktayl.core.screening.domain.ScreeningStore;
import com.ktayl.core.screening.domain.Verdict;
import com.ktayl.core.screening.domain.Vigilance;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound HTTP adapter for the screening module (hexagonal, ADR-013) — maps HTTP onto the published
 * {@link ScreeningGate} + the {@link DecideAlert} use case, nothing more (no business logic). SSO-gated by the
 * shared resource-server; the actor is taken from the token for audit attribution. The decision endpoint is
 * additionally group-gated ({@code Compliance}) via method security — only a compliance officer decides an
 * alert (HITL). Domain errors are mapped to HTTP by {@link ScreeningExceptionHandler} (4xx, never 5xx).
 */
@RestController
@RequestMapping("/v1/screening")
public class ScreeningController {

    public record ScreenRequest(String external_ref, String name, LocalDate dob, String nationality,
            String residence) {}

    public record ScreenResponse(String verdict, UUID alert_id, String dataset_version) {}

    public record PartyStatusResponse(UUID party_id, String status) {}

    public record AlertResponse(UUID alert_id, UUID screening_id, UUID party_id, String bucket, int score,
            String matched_entry, String case_status) {}

    /** The list envelope, carried verbatim from the policy module: {@code {"items":[…],"next_cursor":…}}. */
    public record ListAlertsResponse(List<AlertResponse> items, String next_cursor) {}

    public record DecisionRequest(String status, String risk, String vigilance, String rationale) {}

    private final ScreeningGate gate;
    private final ScreeningStore store;
    private final DecideAlert decideAlert;

    public ScreeningController(ScreeningGate gate, ScreeningStore store, DecideAlert decideAlert) {
        this.gate = gate;
        this.store = store;
        this.decideAlert = decideAlert;
    }

    /** Screen a party at a gate. 200 with the verdict (HOLD on a hit or a fail-closed engine outage). */
    @PostMapping("/screen")
    public ScreenResponse screen(@RequestBody ScreenRequest r, @RequestParam(defaultValue = "intake") String gateName,
            @AuthenticationPrincipal Jwt jwt) {
        PartyRef party = new PartyRef(r.external_ref(), r.name(), r.dob(), r.nationality(), r.residence());
        ScreeningOutcome outcome = gate.screen(party, gateName, actor(jwt));
        return new ScreenResponse(outcome.verdict(), outcome.alertId(), outcome.datasetVersion());
    }

    /** The latest screening status for a party. 404 if the party was never screened / unknown. */
    @GetMapping("/parties/{id}")
    public PartyStatusResponse status(@PathVariable UUID id) {
        Verdict verdict = store.findLatestStatus(id)
                .orElseThrow(() -> new IllegalArgumentException("no screening for party " + id));
        return new PartyStatusResponse(id, verdict.name());
    }

    /**
     * List alerts — the policy list contract carried forward: always {@code 200} with a
     * {@code {"items":[…],"next_cursor":…}} envelope. Optional {@code ?status=} (ACC/REF/PENDING, where
     * PENDING covers an undecided alert) + {@code ?bucket=} filters; {@code ?cursor=} (opaque) + {@code ?limit=}
     * (1–100, default 20). An invalid status/bucket/cursor → 400.
     */
    @GetMapping("/alerts")
    public ListAlertsResponse listAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        int size = clampLimit(limit);
        CaseStatus statusFilter = parseStatus(status);
        Bucket bucketFilter = parseBucket(bucket);
        UUID after = (cursor == null || cursor.isBlank()) ? null : ScreeningCursor.decode(cursor);
        List<AlertView> page = store.listAlerts(statusFilter, bucketFilter, after, size);
        List<AlertResponse> items = page.stream().map(ScreeningController::view).toList();
        String next = (!page.isEmpty() && page.size() == size)
                ? ScreeningCursor.encode(page.get(page.size() - 1).alertId())
                : null;
        return new ListAlertsResponse(items, next);
    }

    /** HITL decision on an alert (server-side authority). 200; 404 unknown alert; 422 invalid; group-gated. */
    @PostMapping("/alerts/{id}/decision")
    @PreAuthorize("hasAuthority('GROUP_Compliance')")
    public ResponseEntity<Void> decide(@PathVariable UUID id, @RequestBody DecisionRequest r,
            @AuthenticationPrincipal Jwt jwt) {
        decideAlert.decide(id, parseRequiredStatus(r.status()), parseRisk(r.risk()),
                parseVigilance(r.vigilance()), r.rationale(), actor(jwt));
        return ResponseEntity.ok().build();
    }

    private static int clampLimit(Integer limit) {
        return limit == null ? 20 : Math.max(1, Math.min(limit, 100));
    }

    private static CaseStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CaseStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid status: " + status);
        }
    }

    private static CaseStatus parseRequiredStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        return parseStatus(status);
    }

    private static Bucket parseBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            return null;
        }
        try {
            return Bucket.valueOf(bucket.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid bucket: " + bucket);
        }
    }

    private static Risk parseRisk(String risk) {
        if (risk == null || risk.isBlank()) {
            return null;
        }
        try {
            return Risk.valueOf(risk.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid risk: " + risk);
        }
    }

    private static Vigilance parseVigilance(String vigilance) {
        if (vigilance == null || vigilance.isBlank()) {
            return null;
        }
        try {
            return Vigilance.valueOf(vigilance.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid vigilance: " + vigilance);
        }
    }

    private static String actor(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return username != null ? username : jwt.getSubject();
    }

    private static AlertResponse view(AlertView a) {
        return new AlertResponse(a.alertId(), a.screeningId(), a.partyId(), a.bucket().name(), a.score(),
                a.matchedEntry(), a.caseStatus() == null ? null : a.caseStatus().name());
    }
}
