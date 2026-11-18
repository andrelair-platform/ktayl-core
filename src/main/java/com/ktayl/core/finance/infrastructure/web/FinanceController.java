package com.ktayl.core.finance.infrastructure.web;

import com.ktayl.core.finance.api.FinanceCloseOperations;
import com.ktayl.core.finance.api.PeriodReconciliation;
import com.ktayl.core.finance.api.PremiumRegister;
import com.ktayl.core.finance.domain.InvalidPeriodException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound HTTP adapter for the finance module (hexagonal, ADR-013) — maps HTTP onto the published
 * {@link FinanceCloseOperations} port and nothing more (no business logic). SSO-gated by the shared
 * resource-server (a valid Authentik token → else 401) and, via method security, the {@code Finance} group
 * (else 403). Both endpoints are read-only. A malformed period path → 400 via {@link FinanceExceptionHandler}.
 */
@RestController
@RequestMapping("/api/finance")
@PreAuthorize("hasAuthority('GROUP_Finance')")
public class FinanceController {

    private final FinanceCloseOperations finance;

    public FinanceController(FinanceCloseOperations finance) {
        this.finance = finance;
    }

    @GetMapping("/periods/{period}/reconciliation")
    public PeriodReconciliation reconciliation(@PathVariable String period) {
        return finance.reconcile(parse(period));
    }

    @GetMapping("/periods/{period}/register")
    public PremiumRegister register(@PathVariable String period) {
        return finance.register(parse(period));
    }

    /** Parse a {@code YYYY-MM} path segment; a bad value is a 400, never a 500. */
    private static YearMonth parse(String period) {
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException ex) {
            throw new InvalidPeriodException(period);
        }
    }
}
