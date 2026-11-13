package com.ktayl.core.complaints.infrastructure.web;

import com.ktayl.core.complaints.api.ComplaintOperations;
import com.ktayl.core.complaints.api.ComplaintSummary;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound HTTP adapter for the complaints module (hexagonal, ADR-013) — it maps HTTP onto the published
 * {@link ComplaintOperations} port and nothing more (no business logic). SSO-gated by the shared
 * resource-server (a valid Authentik token → else 401) and, via method security, the
 * {@code Complaints Handler} group (else 403). The acting principal is taken from the token for audit
 * attribution. Domain exceptions are mapped to HTTP by {@link ComplaintExceptionHandler}.
 */
@RestController
@RequestMapping("/api/complaints")
@PreAuthorize("hasAuthority('GROUP_Complaints Handler')")
public class ComplaintController {

    private final ComplaintOperations complaints;

    public ComplaintController(ComplaintOperations complaints) {
        this.complaints = complaints;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> register(@RequestBody RegisterRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        UUID id = complaints.register(request.channel(), request.subjectRef(), request.summary(),
                idempotencyKey, actor(jwt));
        return ResponseEntity.created(URI.create("/api/complaints/" + id)).body(Map.of("id", id));
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Void> acknowledge(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        complaints.acknowledge(id, actor(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Void> assign(@PathVariable UUID id, @RequestBody AssignRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        complaints.assign(id, request.assignedTo(), actor(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable UUID id, @RequestBody ResolveRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        complaints.resolve(id, request.resolutionNote(), actor(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Void> close(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        complaints.close(id, actor(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ComplaintSummary get(@PathVariable UUID id) {
        return complaints.get(id);
    }

    /** The authenticated principal for audit attribution — the human-readable username, else the subject. */
    private static String actor(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return username != null ? username : jwt.getSubject();
    }

    public record RegisterRequest(String channel, String subjectRef, String summary) {}

    public record AssignRequest(String assignedTo) {}

    public record ResolveRequest(String resolutionNote) {}
}
