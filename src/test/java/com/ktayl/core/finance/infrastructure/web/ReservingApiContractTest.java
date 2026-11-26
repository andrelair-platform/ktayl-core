package com.ktayl.core.finance.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktayl.core.finance.api.IbnrEstimateView;
import com.ktayl.core.finance.api.ReserveReviewView;
import com.ktayl.core.finance.api.ReservingOperations;
import com.ktayl.core.finance.domain.reserving.ReserveReviewNotFoundException;
import com.ktayl.core.finance.domain.reserving.SelfApprovalException;
import com.ktayl.core.finance.domain.reserving.TriangleShapeException;
import com.ktayl.core.shared.config.SecurityConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
 * L3 — the reserving HTTP contract (FIN-03): endpoint shapes + SSO (401 no token, 403 missing the Finance
 * group) + the domain→HTTP mapping (ragged triangle → 422, self-approval → 409, missing review → 404). A
 * @WebMvcTest slice with the real {@link SecurityConfig} + {@link FinanceExceptionHandler}; the published port
 * is mocked (proves the wire contract, not the actuarial math — that is L1/L2).
 */
@WebMvcTest(ReservingController.class)
@Import({SecurityConfig.class, FinanceExceptionHandler.class})
class ReservingApiContractTest {

    @Autowired MockMvc mvc;
    @MockitoBean ReservingOperations reserving;
    @MockitoBean JwtDecoder jwtDecoder;

    private static JwtRequestPostProcessor maker() {
        return jwt().jwt(j -> j.subject("sub-1").claim("preferred_username", "olivia.maker"))
                .authorities(new SimpleGrantedAuthority("GROUP_Finance"));
    }

    private static JwtRequestPostProcessor checker() {
        return jwt().jwt(j -> j.subject("sub-2").claim("preferred_username", "marcus.checker"))
                .authorities(new SimpleGrantedAuthority("GROUP_Finance"));
    }

    private static JwtRequestPostProcessor wrongGroup() {
        return jwt().jwt(j -> j.subject("sub-3")).authorities(new SimpleGrantedAuthority("GROUP_Claims Handler"));
    }

    private static ReserveReviewView review(UUID id, String state) {
        return new ReserveReviewView(id, "marine", LocalDate.parse("2026-09-30"), "INCURRED", "CHAIN_LADDER",
                "EUR", 117500L, 195000L, 77500L, state, "olivia.maker",
                "APPROVED".equals(state) ? "marcus.checker" : null, "APPROVED".equals(state) ? 77500L : null,
                null, Instant.parse("2026-10-01T09:00:00Z"), null, null);
    }

    @Test
    void estimate_returns_200_with_totals() throws Exception {
        when(reserving.estimate(any(), any(), any(), any(), any())).thenReturn(new IbnrEstimateView(
                "CHAIN_LADDER", "EUR", 117500L, 195000L, 77500L, List.of()));
        mvc.perform(post("/api/finance/reserving/estimate").with(maker()).contentType("application/json")
                        .content("{\"portfolio\":\"marine\",\"asOf\":\"2026-09-30\",\"basis\":\"INCURRED\",\"method\":\"CHAIN_LADDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIbnrMinor").value(77500))
                .andExpect(jsonPath("$.method").value("CHAIN_LADDER"));
    }

    @Test
    void load_triangle_returns_204() throws Exception {
        mvc.perform(post("/api/finance/reserving/triangles").with(maker()).contentType("application/json")
                        .content("{\"portfolio\":\"marine\",\"asOf\":\"2026-09-30\",\"basis\":\"PAID\",\"currency\":\"EUR\",\"cells\":[{\"accidentPeriod\":\"2026-Q1\",\"developmentLag\":0,\"cumulativeMinor\":100}]}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void submit_review_returns_201() throws Exception {
        UUID id = UUID.randomUUID();
        when(reserving.submitReview(any(), eq("olivia.maker"))).thenReturn(review(id, "PENDING"));
        mvc.perform(post("/api/finance/reserving/reviews").with(maker()).contentType("application/json")
                        .content("{\"portfolio\":\"marine\",\"asOf\":\"2026-09-30\",\"basis\":\"INCURRED\",\"method\":\"CHAIN_LADDER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.estimatedIbnrMinor").value(77500));
    }

    @Test
    void approve_returns_200_and_passes_the_checker_from_the_token() throws Exception {
        UUID id = UUID.randomUUID();
        when(reserving.approve(eq(id), any(), eq("marcus.checker"))).thenReturn(review(id, "APPROVED"));
        mvc.perform(post("/api/finance/reserving/reviews/" + id + "/approve").with(checker())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.bookedMovementMinor").value(77500));
    }

    @Test
    void a_self_approval_maps_to_409() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new SelfApprovalException("olivia.maker"))
                .when(reserving).approve(eq(id), any(), anyString());
        mvc.perform(post("/api/finance/reserving/reviews/" + id + "/approve").with(maker())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void a_ragged_triangle_maps_to_422() throws Exception {
        doThrow(new TriangleShapeException("not a staircase"))
                .when(reserving).estimate(any(), any(), any(), any(), any());
        mvc.perform(post("/api/finance/reserving/estimate").with(maker()).contentType("application/json")
                        .content("{\"portfolio\":\"marine\",\"asOf\":\"2026-09-30\",\"basis\":\"PAID\",\"method\":\"CHAIN_LADDER\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void a_missing_review_maps_to_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(reserving.getReview(id)).thenThrow(new ReserveReviewNotFoundException(id));
        mvc.perform(get("/api/finance/reserving/reviews/" + id).with(maker()))
                .andExpect(status().isNotFound());
    }

    @Test
    void no_token_is_401() throws Exception {
        mvc.perform(get("/api/finance/reserving/reviews")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrong_group_is_403() throws Exception {
        mvc.perform(get("/api/finance/reserving/reviews").with(wrongGroup())).andExpect(status().isForbidden());
    }
}
