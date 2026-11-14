package com.ktayl.core.policy.infrastructure.web;

import com.ktayl.core.policy.application.CreatePolicyCommand;
import com.ktayl.core.policy.application.PolicyService;
import com.ktayl.core.policy.domain.Policy;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
     * {@code {"items":[…]}} envelope (NOT a single object / 404). Underwriting's 409-resolve reads this and
     * filters client-side by policy_number, so repointing it at ktayl-core needs a base-URL change only.
     * Phase 1 supports the {@code policy_number} filter (the only thing the bind consumer uses); full
     * cursor/status pagination (BF-POL-04) is Phase 1b — hence an empty page when no filter is given.
     */
    @GetMapping
    public ListPoliciesResponse list(@RequestParam(name = "policy_number", required = false) String policyNumber) {
        java.util.List<PolicyResponse> items = (policyNumber == null || policyNumber.isBlank())
                ? java.util.List.of() // full listing is Phase 1b; the bind path only ever filters by number
                : policies.findByPolicyNumber(policyNumber).map(p -> java.util.List.of(view(p))).orElseGet(java.util.List::of);
        return new ListPoliciesResponse(items, null);
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
