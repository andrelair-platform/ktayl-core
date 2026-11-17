package com.ktayl.core.submission.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktayl.core.shared.config.SecurityConfig;
import com.ktayl.core.submission.api.SubmissionIntake;
import com.ktayl.core.submission.api.SubmissionQuery;
import com.ktayl.core.submission.api.SubmissionView;
import com.ktayl.core.submission.domain.IllegalSubmissionTransitionException;
import com.ktayl.core.submission.domain.InvalidSubmissionException;
import com.ktayl.core.submission.domain.SubmissionNotFoundException;
import com.ktayl.core.submission.domain.SubmissionScreeningHoldException;
import com.ktayl.core.submission.domain.SubmissionStatus;
import java.time.Instant;
import java.util.Optional;
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
 * L3 — the Submission HTTP contract: endpoint shapes, the exception→status mapping (404/409/422/400), and
 * SSO (401 no token, 403 missing the Underwriting group; clear-hold needs Compliance). A @WebMvcTest slice
 * with the real {@link SecurityConfig} + {@link SubmissionExceptionHandler}; the published ports are mocked
 * (this proves the wire contract, not the business logic — that is L1/L2).
 */
@WebMvcTest(SubmissionController.class)
@Import({SecurityConfig.class, SubmissionExceptionHandler.class})
class SubmissionApiContractTest {

    @Autowired MockMvc mvc;
    @MockitoBean SubmissionIntake intake;
    @MockitoBean SubmissionQuery query;
    @MockitoBean JwtDecoder jwtDecoder; // satisfies the resource-server wiring; jwt() post-processor bypasses it

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    /** An authenticated request by a member of the Underwriting group, with a username for attribution. */
    private static JwtRequestPostProcessor underwriter() {
        return jwt()
                .jwt(j -> j.subject("sub-1").claim("preferred_username", "agent.kanmegnea"))
                .authorities(new SimpleGrantedAuthority("GROUP_Underwriting"));
    }

    private static JwtRequestPostProcessor compliance() {
        return jwt()
                .jwt(j -> j.subject("sub-2").claim("preferred_username", "officer.sophie"))
                .authorities(new SimpleGrantedAuthority("GROUP_Compliance"));
    }

    @Test
    void register_returns_201_with_the_new_id_and_location() throws Exception {
        when(intake.register(any(), any())).thenReturn(ID);

        mvc.perform(post("/v1/submissions").with(underwriter())
                        .contentType("application/json")
                        .content("{\"broker_ref\":\"BRK-1\",\"insured_name\":\"ACME SA\",\"product_line\":\"MARINE\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/submissions/" + ID))
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    void add_exposure_returns_201_with_the_exposure_id() throws Exception {
        UUID exposureId = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
        when(intake.addExposure(eq(ID), any(), any())).thenReturn(exposureId);

        mvc.perform(post("/v1/submissions/{id}/exposures", ID).with(underwriter())
                        .contentType("application/json")
                        .content("{\"site_name\":\"Rotterdam\",\"country\":\"NL\",\"tiv_amount\":1000000,"
                                + "\"currency\":\"EUR\",\"cat_flag\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(exposureId.toString()));
    }

    @Test
    void get_returns_the_view_never_the_entity() throws Exception {
        when(query.findById(ID)).thenReturn(Optional.of(new SubmissionView(ID, "BRK-1", "ACME SA", "MARINE",
                SubmissionStatus.RECEIVED.name(), false, null, Instant.parse("2026-11-17T10:15:30Z"))));

        mvc.perform(get("/v1/submissions/{id}", ID).with(underwriter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.broker_ref").value("BRK-1"))
                .andExpect(jsonPath("$.screening_hold").value(false));
    }

    @Test
    void unknown_id_is_404() throws Exception {
        when(query.findById(ID)).thenReturn(Optional.empty());
        mvc.perform(get("/v1/submissions/{id}", ID).with(underwriter()))
                .andExpect(status().isNotFound());
    }

    @Test
    void an_illegal_transition_is_409() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalSubmissionTransitionException(
                        SubmissionStatus.RECEIVED, SubmissionStatus.TRIAGED))
                .when(intake).route(any(), any(), any());
        mvc.perform(post("/v1/submissions/{id}/route", ID).with(underwriter())
                        .contentType("application/json").content("{\"target\":\"underwriting\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void routing_a_held_submission_is_409_with_the_alert_id() throws Exception {
        UUID alert = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
        org.mockito.Mockito.doThrow(new SubmissionScreeningHoldException(ID, alert))
                .when(intake).route(any(), any(), any());
        mvc.perform(post("/v1/submissions/{id}/route", ID).with(underwriter())
                        .contentType("application/json").content("{\"target\":\"underwriting\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.alertId").value(alert.toString()))
                .andExpect(jsonPath("$.detail").value("routing blocked pending sanctions review"));
    }

    @Test
    void invalid_input_is_422() throws Exception {
        when(intake.register(any(), any()))
                .thenThrow(new InvalidSubmissionException("insured name is required"));
        mvc.perform(post("/v1/submissions").with(underwriter())
                        .contentType("application/json")
                        .content("{\"broker_ref\":\"BRK-1\",\"product_line\":\"MARINE\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void an_invalid_list_cursor_is_400() throws Exception {
        mvc.perform(get("/v1/submissions").param("cursor", "!!notbase64!!").with(underwriter()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void no_token_is_401() throws Exception {
        mvc.perform(get("/v1/submissions/{id}", ID)).andExpect(status().isUnauthorized());
    }

    @Test
    void a_token_without_the_underwriting_group_is_403() throws Exception {
        mvc.perform(get("/v1/submissions/{id}", ID).with(jwt())) // authenticated, no GROUP_ authority
                .andExpect(status().isForbidden());
    }

    @Test
    void clear_hold_needs_the_compliance_group() throws Exception {
        // an underwriter (not Compliance) is forbidden from clearing a hold
        mvc.perform(post("/v1/submissions/{id}/clear-hold", ID).with(underwriter()))
                .andExpect(status().isForbidden());
        // a compliance officer can
        mvc.perform(post("/v1/submissions/{id}/clear-hold", ID).with(compliance()))
                .andExpect(status().isNoContent());
    }

    @Test
    void malformed_json_is_4xx_not_5xx() throws Exception {
        mvc.perform(post("/v1/submissions").with(underwriter())
                        .contentType("application/json").content("{ not json"))
                .andExpect(status().isBadRequest());
    }
}
