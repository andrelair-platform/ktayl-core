package com.ktayl.core.finance.infrastructure.web;

import com.ktayl.core.finance.api.IbnrEstimateView;
import com.ktayl.core.finance.api.ReserveReviewRequest;
import com.ktayl.core.finance.api.ReserveReviewView;
import com.ktayl.core.finance.api.ReservingOperations;
import com.ktayl.core.finance.api.TriangleLoadRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound HTTP adapter for actuarial reserving (FIN-03, hexagonal) — maps HTTP onto the published
 * {@link ReservingOperations}, no business logic. SSO-gated by the shared resource-server (401 without a valid
 * Authentik token) and, via method security, the {@code Finance} group (403 otherwise). The acting principal is
 * taken from the token for maker/checker attribution — the <b>maker</b> on submit, the <b>checker</b> on
 * approve/reject (the segregation-of-duties control compares the two). Domain failures map to 404/409/422 via
 * {@link FinanceExceptionHandler}, never a 500.
 */
@RestController
@RequestMapping("/api/finance/reserving")
@PreAuthorize("hasAuthority('GROUP_Finance')")
public class ReservingController {

    private final ReservingOperations reserving;

    public ReservingController(ReservingOperations reserving) {
        this.reserving = reserving;
    }

    /** Load (replace) a loss triangle. */
    @PostMapping("/triangles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void loadTriangle(@RequestBody TriangleLoadRequest request, @AuthenticationPrincipal Jwt jwt) {
        reserving.loadTriangle(request, actor(jwt));
    }

    /** Dry-run an IBNR estimate (no persistence) — lets a reviewer compare chain-ladder vs BF before submitting. */
    @PostMapping("/estimate")
    public IbnrEstimateView estimate(@RequestBody EstimateRequest request) {
        return reserving.estimate(request.portfolio(), request.asOf(), request.basis(), request.method(),
                request.aprioriUltimateMinor());
    }

    /** Submit an IBNR reserve for four-eyes review (the caller is the maker). */
    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReserveReviewView submit(@RequestBody ReserveReviewRequest request, @AuthenticationPrincipal Jwt jwt) {
        return reserving.submitReview(request, actor(jwt));
    }

    /** Approve a PENDING review (the caller is the checker; must differ from the maker) → books to the GL. */
    @PostMapping("/reviews/{id}/approve")
    public ReserveReviewView approve(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return reserving.approve(id, request != null ? request.reason() : null, actor(jwt));
    }

    /** Reject a PENDING review with a reason (the caller is the checker; must differ from the maker). */
    @PostMapping("/reviews/{id}/reject")
    public ReserveReviewView reject(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return reserving.reject(id, request != null ? request.reason() : null, actor(jwt));
    }

    @GetMapping("/reviews/{id}")
    public ReserveReviewView getReview(@PathVariable UUID id) {
        return reserving.getReview(id);
    }

    @GetMapping("/reviews")
    public List<ReserveReviewView> listReviews(@RequestParam(required = false) String portfolio) {
        return reserving.listReviews(portfolio);
    }

    private static String actor(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return username != null ? username : jwt.getSubject();
    }

    public record EstimateRequest(String portfolio, LocalDate asOf, String basis, String method,
            Map<String, Long> aprioriUltimateMinor) {}

    public record DecisionRequest(String reason) {}
}
