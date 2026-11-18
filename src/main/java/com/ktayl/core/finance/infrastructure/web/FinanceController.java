package com.ktayl.core.finance.infrastructure.web;

import com.ktayl.core.finance.api.FinanceCloseOperations;
import com.ktayl.core.finance.api.PeriodCloseOperations;
import com.ktayl.core.finance.api.PeriodCloseRecord;
import com.ktayl.core.finance.api.PeriodReconciliation;
import com.ktayl.core.finance.api.PremiumRegister;
import com.ktayl.core.finance.domain.InvalidPeriodException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound HTTP adapter for the finance module (hexagonal, ADR-013) — maps HTTP onto the published
 * {@link FinanceCloseOperations} (read) + {@link PeriodCloseOperations} (close/reopen) ports, no business
 * logic. SSO-gated by the shared resource-server (valid Authentik token → else 401) and, via method security,
 * the {@code Finance} group (else 403); the acting principal is taken from the token for audit attribution. A
 * malformed period path → 400, and the close/reopen domain failures → 409/422, via
 * {@link FinanceExceptionHandler}.
 */
@RestController
@RequestMapping("/api/finance")
@PreAuthorize("hasAuthority('GROUP_Finance')")
public class FinanceController {

    private final FinanceCloseOperations finance;
    private final PeriodCloseOperations periodClose;

    public FinanceController(FinanceCloseOperations finance, PeriodCloseOperations periodClose) {
        this.finance = finance;
        this.periodClose = periodClose;
    }

    @GetMapping("/periods/{period}/reconciliation")
    public PeriodReconciliation reconciliation(@PathVariable String period) {
        return finance.reconcile(parse(period));
    }

    @GetMapping("/periods/{period}/register")
    public PremiumRegister register(@PathVariable String period) {
        return finance.register(parse(period));
    }

    @PostMapping("/periods/{period}/close")
    public ResponseEntity<Void> close(@PathVariable String period,
            @RequestBody(required = false) CloseRequest request, @AuthenticationPrincipal Jwt jwt) {
        boolean force = request != null && Boolean.TRUE.equals(request.force());
        String reason = request != null ? request.reason() : null;
        periodClose.close(parse(period), force, reason, actor(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/periods/{period}/reopen")
    public ResponseEntity<Void> reopen(@PathVariable String period, @RequestBody ReopenRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        periodClose.reopen(parse(period), request != null ? request.reason() : null, actor(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/periods/{period}/close")
    public List<PeriodCloseRecord> closeHistory(@PathVariable String period) {
        return periodClose.closeHistory(parse(period));
    }

    /** Parse a {@code YYYY-MM} path segment; a bad value is a 400, never a 500. */
    private static YearMonth parse(String period) {
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException ex) {
            throw new InvalidPeriodException(period);
        }
    }

    /** The authenticated principal for audit attribution — the human-readable username, else the subject. */
    private static String actor(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return username != null ? username : jwt.getSubject();
    }

    public record CloseRequest(Boolean force, String reason) {}

    public record ReopenRequest(String reason) {}
}
