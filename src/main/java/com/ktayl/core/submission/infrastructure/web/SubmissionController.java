package com.ktayl.core.submission.infrastructure.web;

import com.ktayl.core.submission.api.AddExposureCommand;
import com.ktayl.core.submission.api.RegisterSubmissionCommand;
import com.ktayl.core.submission.api.RouteSubmissionCommand;
import com.ktayl.core.submission.api.SubmissionIntake;
import com.ktayl.core.submission.api.SubmissionQuery;
import com.ktayl.core.submission.api.SubmissionView;
import com.ktayl.core.submission.api.TriageSubmissionCommand;
import com.ktayl.core.submission.domain.SubmissionNotFoundException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
 * Inbound HTTP adapter for the submission module (hexagonal, ADR-013) — it maps HTTP onto the published
 * {@link SubmissionIntake} + {@link SubmissionQuery} ports and nothing more (no business logic). SSO-gated by
 * the shared resource-server (a valid Authentik token → else 401) and, via method security, the
 * {@code Underwriting} group (else 403); clearing a sanctions hold is additionally gated to the
 * {@code Compliance} group (HITL — only a compliance officer clears a hold). The acting principal is taken
 * from the token for audit attribution. snake_case request/response records (the PAS house wire style).
 * Domain exceptions are mapped to HTTP by {@link SubmissionExceptionHandler}.
 */
@RestController
@RequestMapping("/v1/submissions")
@PreAuthorize("hasAuthority('GROUP_Underwriting')")
public class SubmissionController {

    public record RegisterRequest(String broker_ref, String insured_name, String product_line) {}

    public record ExposureRequest(String site_name, String country, BigDecimal tiv_amount, String currency,
            String occupancy, String postcode, String loss_history, boolean cat_flag) {}

    public record TriageRequest(String decision, Integer completeness_score, String appetite_flag,
            String reason) {}

    public record RouteRequest(String target) {}

    public record SubmissionResponse(UUID id, String broker_ref, String insured_name, String product_line,
            String status, boolean screening_hold, String routed_target, String uw_submission_ref,
            Instant received_at) {}

    /** The list envelope, carried from the policy module: {@code {"items":[…],"next_cursor":…}}. */
    public record ListSubmissionsResponse(List<SubmissionResponse> items, String next_cursor) {}

    private final SubmissionIntake intake;
    private final SubmissionQuery query;

    public SubmissionController(SubmissionIntake intake, SubmissionQuery query) {
        this.intake = intake;
        this.query = query;
    }

    /** Capture a submission (screens the insured at intake). 201 created with the new id + Location. */
    @PostMapping
    public ResponseEntity<Map<String, UUID>> register(@RequestBody RegisterRequest r,
            @AuthenticationPrincipal Jwt jwt) {
        UUID id = intake.register(
                new RegisterSubmissionCommand(r.broker_ref(), r.insured_name(), r.product_line()), actor(jwt));
        return ResponseEntity.created(URI.create("/v1/submissions/" + id)).body(Map.of("id", id));
    }

    /** Append an exposure item to the dossier. 201 created with the new exposure id. */
    @PostMapping("/{id}/exposures")
    public ResponseEntity<Map<String, UUID>> addExposure(@PathVariable UUID id,
            @RequestBody ExposureRequest r, @AuthenticationPrincipal Jwt jwt) {
        UUID exposureId = intake.addExposure(id, new AddExposureCommand(r.site_name(), r.country(),
                r.tiv_amount(), r.currency(), r.occupancy(), r.postcode(), r.loss_history(), r.cat_flag()),
                actor(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", exposureId));
    }

    /** Triage the submission (ROUTE → TRIAGED, DECLINE → DECLINED, RETURN → no change). 204. */
    @PostMapping("/{id}/triage")
    public ResponseEntity<Void> triage(@PathVariable UUID id, @RequestBody TriageRequest r,
            @AuthenticationPrincipal Jwt jwt) {
        intake.triage(id, new TriageSubmissionCommand(r.decision(), r.completeness_score(), r.appetite_flag(),
                r.reason()), actor(jwt));
        return ResponseEntity.noContent().build();
    }

    /** Route a triaged submission onward. 204; 409 if under a sanctions hold or not TRIAGED. */
    @PostMapping("/{id}/route")
    public ResponseEntity<Void> route(@PathVariable UUID id, @RequestBody RouteRequest r,
            @AuthenticationPrincipal Jwt jwt) {
        intake.route(id, new RouteSubmissionCommand(r.target()), actor(jwt));
        return ResponseEntity.noContent().build();
    }

    /** Clear a sanctions hold (compliance HITL). 204; group-gated to Compliance. */
    @PostMapping("/{id}/clear-hold")
    @PreAuthorize("hasAuthority('GROUP_Compliance')")
    public ResponseEntity<Void> clearHold(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        intake.clearScreeningHold(id, actor(jwt));
        return ResponseEntity.noContent().build();
    }

    /**
     * List submissions — always {@code 200} with a {@code {"items":[…],"next_cursor":…}} envelope. Optional
     * {@code ?status=} (RECEIVED/TRIAGED/ROUTED/DECLINED) filter; {@code ?cursor=} (opaque) + {@code ?limit=}
     * (1–100, default 20). An invalid cursor → 400; an unknown status → 422.
     */
    @GetMapping
    public ListSubmissionsResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        int size = clampLimit(limit);
        SubmissionCursor after = (cursor == null || cursor.isBlank()) ? null : SubmissionCursor.decode(cursor);
        List<SubmissionView> page = query.list(status,
                after == null ? null : after.receivedAt(), after == null ? null : after.id(), size);
        List<SubmissionResponse> items = page.stream().map(SubmissionController::view).toList();
        String next = (!page.isEmpty() && page.size() == size)
                ? SubmissionCursor.encode(page.get(page.size() - 1).receivedAt(),
                        page.get(page.size() - 1).id())
                : null;
        return new ListSubmissionsResponse(items, next);
    }

    /** The submission for an id. 404 if unknown. */
    @GetMapping("/{id}")
    public SubmissionResponse get(@PathVariable UUID id) {
        return query.findById(id).map(SubmissionController::view)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
    }

    private static int clampLimit(Integer limit) {
        return limit == null ? 20 : Math.max(1, Math.min(limit, 100));
    }

    /** The authenticated principal for audit attribution — the human-readable username, else the subject. */
    private static String actor(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return username != null ? username : jwt.getSubject();
    }

    private static SubmissionResponse view(SubmissionView v) {
        return new SubmissionResponse(v.id(), v.brokerRef(), v.insuredName(), v.productLine(), v.status(),
                v.screeningHold(), v.routedTarget(), v.uwSubmissionRef(), v.receivedAt());
    }
}
