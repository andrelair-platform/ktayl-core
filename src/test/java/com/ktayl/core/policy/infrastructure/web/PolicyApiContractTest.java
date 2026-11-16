package com.ktayl.core.policy.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktayl.core.policy.application.BindScreeningHoldException;
import com.ktayl.core.policy.application.PolicyService;
import com.ktayl.core.policy.domain.IllegalPolicyTransitionException;
import com.ktayl.core.policy.domain.InvalidPolicyException;
import com.ktayl.core.policy.domain.Policy;
import com.ktayl.core.policy.domain.PolicyAlreadyExistsException;
import com.ktayl.core.policy.domain.PolicyNotFoundException;
import com.ktayl.core.policy.domain.PolicyStatus;
import com.ktayl.core.shared.config.SecurityConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * L3 — the policy HTTP contract carried forward VERBATIM from the PAS (so underwriting repoints by base-URL
 * only, ADR-014 Phase 3): the create/submit/activate/cancel shapes, snake_case fields, the exception→status
 * map (404/409/422/400), and SSO (401 without a token — BF-POL-07; authenticated M2M is allowed, no group
 * gate, unlike complaints). A @WebMvcTest slice with the real {@link SecurityConfig} +
 * {@link PolicyExceptionHandler}; {@link PolicyService} is mocked — this proves the wire contract, not the
 * business logic (that is L1/L2).
 */
@WebMvcTest(PolicyController.class)
@Import({SecurityConfig.class, PolicyExceptionHandler.class})
class PolicyApiContractTest {

    @Autowired MockMvc mvc;
    @MockitoBean PolicyService policies;
    @MockitoBean JwtDecoder jwtDecoder; // satisfies the resource-server wiring; jwt() post-processor bypasses it

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    /** An authenticated principal (the underwriting M2M client); no group authority is required for the bind. */
    private static JwtRequestPostProcessor svc() {
        return jwt().jwt(j -> j.subject("sub-uw").claim("preferred_username", "svc.underwriting"));
    }

    private static Policy policy(PolicyStatus status) {
        return Policy.rehydrate(ID, "POL-1", "ACME SA", "MARINE",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), status,
                Instant.parse("2026-11-15T10:00:00Z"), Instant.parse("2026-11-15T10:00:00Z"));
    }

    @Test
    void create_returns_201_with_the_snake_case_body() throws Exception {
        when(policies.create(any(), any())).thenReturn(policy(PolicyStatus.DRAFT));

        mvc.perform(post("/v1/policies").with(svc())
                        .contentType("application/json")
                        .content("{\"policy_number\":\"POL-1\",\"holder_name\":\"ACME SA\",\"product_code\":\"MARINE\","
                                + "\"effective_date\":\"2026-01-01\",\"expiry_date\":\"2027-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.policy_number").value("POL-1"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void a_duplicate_policy_number_is_409() throws Exception {
        when(policies.create(any(), any())).thenThrow(new PolicyAlreadyExistsException("policy POL-1 already exists"));
        mvc.perform(post("/v1/policies").with(svc())
                        .contentType("application/json")
                        .content("{\"policy_number\":\"POL-1\",\"holder_name\":\"h\",\"product_code\":\"p\","
                                + "\"effective_date\":\"2026-01-01\",\"expiry_date\":\"2027-01-01\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void resolve_by_policy_number_returns_the_pas_list_envelope() throws Exception {
        // the PAS contract (carried forward verbatim): GET is a LIST → {"items":[…]}, never a single object
        when(policies.findByPolicyNumber("POL-1")).thenReturn(Optional.of(policy(PolicyStatus.ACTIVE)));
        mvc.perform(get("/v1/policies").param("policy_number", "POL-1").with(svc()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].policy_number").value("POL-1"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"));
    }

    @Test
    void resolve_of_an_unknown_number_is_200_with_an_empty_list() throws Exception {
        // NOT a 404 — underwriting's 409-resolve treats a non-200 as unresolvable; it needs an empty page
        when(policies.findByPolicyNumber("POL-X")).thenReturn(Optional.empty());
        mvc.perform(get("/v1/policies").param("policy_number", "POL-X").with(svc()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void keyset_list_returns_items_and_a_next_cursor_when_the_page_is_full() throws Exception {
        when(policies.list(any(), any(), any(), anyInt()))
                .thenReturn(List.of(policy(PolicyStatus.DRAFT), policy(PolicyStatus.DRAFT)));
        mvc.perform(get("/v1/policies").param("limit", "2").with(svc()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.next_cursor").isNotEmpty()); // full page → a cursor to continue
    }

    @Test
    void an_invalid_status_filter_is_400() throws Exception {
        mvc.perform(get("/v1/policies").param("status", "NOPE").with(svc()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void an_invalid_cursor_is_400() throws Exception {
        mvc.perform(get("/v1/policies").param("cursor", "!!!not-base64!!!").with(svc()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns_200_with_the_new_details() throws Exception {
        when(policies.update(eq(ID), any(), any())).thenReturn(policy(PolicyStatus.DRAFT));
        mvc.perform(put("/v1/policies/{id}", ID).with(svc())
                        .contentType("application/json")
                        .content("{\"holder_name\":\"ACME RENAMED\",\"product_code\":\"AVIATION\","
                                + "\"effective_date\":\"2026-03-01\",\"expiry_date\":\"2027-03-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    void update_past_draft_is_409() throws Exception {
        when(policies.update(eq(ID), any(), any()))
                .thenThrow(new IllegalPolicyTransitionException("cannot update a SUBMITTED policy"));
        mvc.perform(put("/v1/policies/{id}", ID).with(svc())
                        .contentType("application/json")
                        .content("{\"holder_name\":\"h\",\"product_code\":\"p\","
                                + "\"effective_date\":\"2026-01-01\",\"expiry_date\":\"2027-01-01\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void update_of_an_unknown_id_is_404() throws Exception {
        when(policies.update(eq(ID), any(), any())).thenThrow(new PolicyNotFoundException("no policy " + ID));
        mvc.perform(put("/v1/policies/{id}", ID).with(svc())
                        .contentType("application/json")
                        .content("{\"holder_name\":\"h\",\"product_code\":\"p\","
                                + "\"effective_date\":\"2026-01-01\",\"expiry_date\":\"2027-01-01\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_with_invalid_dates_is_422() throws Exception {
        when(policies.update(eq(ID), any(), any()))
                .thenThrow(new InvalidPolicyException("effective_date must be before expiry_date"));
        mvc.perform(put("/v1/policies/{id}", ID).with(svc())
                        .contentType("application/json")
                        .content("{\"holder_name\":\"h\",\"product_code\":\"p\","
                                + "\"effective_date\":\"2027-01-01\",\"expiry_date\":\"2026-01-01\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void submit_and_activate_return_204() throws Exception {
        when(policies.submit(eq(ID), any())).thenReturn(policy(PolicyStatus.SUBMITTED));
        when(policies.activate(eq(ID), any())).thenReturn(policy(PolicyStatus.ACTIVE));

        mvc.perform(post("/v1/policies/{id}/submit", ID).with(svc())).andExpect(status().isNoContent());
        mvc.perform(post("/v1/policies/{id}/activate", ID).with(svc())).andExpect(status().isNoContent());
    }

    @Test
    void an_illegal_transition_is_409() throws Exception {
        when(policies.activate(eq(ID), any()))
                .thenThrow(new IllegalPolicyTransitionException("cannot activate a DRAFT policy"));
        mvc.perform(post("/v1/policies/{id}/activate", ID).with(svc())).andExpect(status().isConflict());
    }

    @Test
    void a_sanctions_hold_at_bind_is_409_with_gate_and_alert() throws Exception {
        java.util.UUID alert = java.util.UUID.randomUUID();
        when(policies.activate(eq(ID), any()))
                .thenThrow(new BindScreeningHoldException(ID, alert, "bind"));
        mvc.perform(post("/v1/policies/{id}/activate", ID).with(svc()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.gate").value("bind"))
                .andExpect(jsonPath("$.alertId").value(alert.toString()))
                .andExpect(jsonPath("$.detail").value("held pending sanctions review"));
    }

    @Test
    void an_unknown_id_is_404() throws Exception {
        when(policies.get(ID)).thenThrow(new PolicyNotFoundException("no policy " + ID));
        mvc.perform(get("/v1/policies/{id}", ID).with(svc())).andExpect(status().isNotFound());
    }

    @Test
    void no_token_is_401() throws Exception {
        mvc.perform(get("/v1/policies/{id}", ID)).andExpect(status().isUnauthorized());
    }

    @Test
    void malformed_json_is_4xx_not_5xx() throws Exception {
        mvc.perform(post("/v1/policies").with(svc())
                        .contentType("application/json").content("{ not json"))
                .andExpect(status().is4xxClientError());
    }
}
