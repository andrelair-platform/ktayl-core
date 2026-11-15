package com.ktayl.core.policy.infrastructure.web;

import com.ktayl.core.policy.application.CreatePolicyCommand;
import com.ktayl.core.policy.application.PolicyService;
import com.ktayl.core.policy.application.UpdatePolicyCommand;
import com.ktayl.core.policy.domain.Policy;
import com.ktayl.core.policy.domain.PolicyStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Policy API — the PAS REST contract carried forward verbatim from ktayl-policy-service (ADR-014) so
 * underwriting's bind client (create → submit → activate, 409-resolve) works after a base-URL change only.
 * Authenticated via the Authentik OIDC resource-server; the actor is taken from the token (DORA audit).
 * Phase 1 = bind-path + cancel; amend/reject/expire/documents/attestation are Phase 1b.
 */
@RestController
@RequestMapping("/v1/policies")
public class PolicyController {

    public record CreatePolicyRequest(String policy_number, String holder_name, String product_code,
            LocalDate effective_date, LocalDate expiry_date) {}

    public record CancelRequest(String reason) {}

    public record UpdatePolicyRequest(String holder_name, String product_code,
            LocalDate effective_date, LocalDate expiry_date) {}

    public record PolicyResponse(UUID id, String policy_number, String holder_name, String product_code,
            String status, LocalDate effective_date, LocalDate expiry_date) {}

    /** The PAS list envelope, carried forward verbatim: {@code {"items":[…],"next_cursor":…}}. */
    public record ListPoliciesResponse(java.util.List<PolicyResponse> items, String next_cursor) {}

    private final PolicyService policies;

    public PolicyController(PolicyService policies) {
        this.policies = policies;
    }

    /** Create a DRAFT policy. 201 created; 409 if the policy_number already exists (resolve via GET ?policy_number=). */
    @PostMapping
    public ResponseEntity<PolicyResponse> create(@RequestBody CreatePolicyRequest r, @AuthenticationPrincipal Jwt jwt) {
        Policy p = policies.create(new CreatePolicyCommand(r.policy_number(), r.holder_name(), r.product_code(),
                r.effective_date(), r.expiry_date()), actor(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(p));
    }

    /**
     * List policies — the PAS contract carried forward verbatim: always {@code 200} with a
     * {@code {"items":[…],"next_cursor":…}} envelope (NOT a single object / 404). Two modes:
     * <ul>
     *   <li>{@code ?policy_number=} → the 409-resolve lookup (a 0/1-item list; underwriting filters client-side);</li>
     *   <li>otherwise → a keyset page (BF-POL-04): optional {@code ?status=}, {@code ?cursor=} (opaque),
     *       {@code ?limit=} (1–100, default 20). A {@code next_cursor} is returned only when a full page comes back.</li>
     * </ul>
     * An invalid {@code status} or {@code cursor} → {@code 400}.
     */
    @GetMapping
    public ListPoliciesResponse list(
            @RequestParam(name = "policy_number", required = false) String policyNumber,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit) {
        if (policyNumber != null && !policyNumber.isBlank()) {
            List<PolicyResponse> items = policies.findByPolicyNumber(policyNumber)
                    .map(p -> List.of(view(p))).orElseGet(List::of);
            return new ListPoliciesResponse(items, null);
        }
        int size = clampLimit(limit);
        PolicyStatus statusFilter = parseStatus(status);
        PolicyCursor after = (cursor == null || cursor.isBlank()) ? null : PolicyCursor.decode(cursor);
        List<Policy> page = policies.list(statusFilter,
                after == null ? null : after.createdAt, after == null ? null : after.id, size);
        List<PolicyResponse> items = page.stream().map(PolicyController::view).toList();
        String next = (!page.isEmpty() && page.size() == size)
                ? PolicyCursor.encode(page.get(page.size() - 1).createdAt(), page.get(page.size() - 1).id())
                : null;
        return new ListPoliciesResponse(items, next);
    }

    /** Update a DRAFT policy's details (BF-POL-04). 200; 404 unknown; 409 if past DRAFT; 422 invalid. */
    @PutMapping("/{id}")
    public PolicyResponse update(@PathVariable UUID id, @RequestBody UpdatePolicyRequest r, @AuthenticationPrincipal Jwt jwt) {
        return view(policies.update(id, new UpdatePolicyCommand(r.holder_name(), r.product_code(),
                r.effective_date(), r.expiry_date()), actor(jwt)));
    }

    private static int clampLimit(Integer limit) {
        return limit == null ? 20 : Math.max(1, Math.min(limit, 100));
    }

    private static PolicyStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PolicyStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid status: " + status);
        }
    }

    @GetMapping("/{id}")
    public PolicyResponse get(@PathVariable UUID id) {
        return view(policies.get(id));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<Void> submit(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        policies.submit(id, actor(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        policies.activate(id, actor(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id, @RequestBody CancelRequest r, @AuthenticationPrincipal Jwt jwt) {
        policies.cancel(id, r == null ? null : r.reason(), actor(jwt));
        return ResponseEntity.noContent().build();
    }

    private static String actor(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return username != null ? username : jwt.getSubject();
    }

    private static PolicyResponse view(Policy p) {
        return new PolicyResponse(p.id(), p.policyNumber(), p.holderName(), p.productCode(),
                p.status().name(), p.effectiveDate(), p.expiryDate());
    }
}
