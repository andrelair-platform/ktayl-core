package com.ktayl.core.finance.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktayl.core.finance.api.FinanceCloseOperations;
import com.ktayl.core.finance.api.PeriodReconciliation;
import com.ktayl.core.finance.api.PremiumRegister;
import com.ktayl.core.shared.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * L3 — the finance HTTP contract: endpoint shapes, the period→400 mapping, and SSO (401 no token, 403 missing
 * the Finance group, 200 with it). A @WebMvcTest slice with the real {@link SecurityConfig} +
 * {@link FinanceExceptionHandler}; the published port is mocked (proves the wire contract, not the math —
 * that is L1/L2).
 */
@WebMvcTest(FinanceController.class)
@Import({SecurityConfig.class, FinanceExceptionHandler.class})
class FinanceApiContractTest {

    @Autowired MockMvc mvc;
    @MockitoBean FinanceCloseOperations finance;
    @MockitoBean JwtDecoder jwtDecoder; // satisfies resource-server wiring; jwt() post-processor bypasses it

    private static JwtRequestPostProcessor financeOfficer() {
        return jwt().jwt(j -> j.subject("sub-1").claim("preferred_username", "officer.sophie"))
                .authorities(new SimpleGrantedAuthority("GROUP_Finance"));
    }

    private static JwtRequestPostProcessor wrongGroup() {
        return jwt().jwt(j -> j.subject("sub-2"))
                .authorities(new SimpleGrantedAuthority("GROUP_Complaints Handler"));
    }

    @Test
    void reconciliation_returns_200_for_a_finance_officer() throws Exception {
        when(finance.reconcile(any())).thenReturn(new PeriodReconciliation(
                "2026-10", "OPEN", 780_000L, null, "UNAVAILABLE", 0L, 1L, 120_000L, 1L, 120_000L, List.of()));

        mvc.perform(get("/api/finance/periods/2026-10/reconciliation").with(financeOfficer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subledgerPostedMinor").value(780000))
                .andExpect(jsonPath("$.glStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.failedCount").value(1));
    }

    @Test
    void register_returns_200_for_a_finance_officer() throws Exception {
        when(finance.register(any())).thenReturn(
                new PremiumRegister("2026-10", 600_000L, 420_000L, 180_000L, "EUR"));

        mvc.perform(get("/api/finance/periods/2026-10/register").with(financeOfficer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivableOutstandingMinor").value(180000))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void malformed_period_is_400() throws Exception {
        mvc.perform(get("/api/finance/periods/2026-13/reconciliation").with(financeOfficer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void no_token_is_401() throws Exception {
        mvc.perform(get("/api/finance/periods/2026-10/reconciliation"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrong_group_is_403() throws Exception {
        mvc.perform(get("/api/finance/periods/2026-10/reconciliation").with(wrongGroup()))
                .andExpect(status().isForbidden());
    }
}
