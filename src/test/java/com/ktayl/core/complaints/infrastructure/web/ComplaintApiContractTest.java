package com.ktayl.core.complaints.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktayl.core.complaints.api.ComplaintOperations;
import com.ktayl.core.complaints.api.ComplaintSummary;
import com.ktayl.core.complaints.domain.ComplaintNotFoundException;
import com.ktayl.core.complaints.domain.IllegalComplaintTransitionException;
import com.ktayl.core.complaints.domain.ComplaintStatus;
import com.ktayl.core.complaints.domain.InvalidComplaintException;
import com.ktayl.core.shared.config.SecurityConfig;
import java.time.Instant;
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
 * L3 — the Complaints HTTP contract: endpoint shapes, the exception→status mapping (404/409/422), and SSO
 * (401 no token, 403 missing the Complaints Handler group). A @WebMvcTest slice with the real
 * {@link SecurityConfig} + {@link ComplaintExceptionHandler}; the published {@link ComplaintOperations} is
 * mocked (this proves the wire contract, not the business logic — that is the L1/L2 layers). Must match
 * {@code api/complaints.openapi.yaml}.
 */
@WebMvcTest(ComplaintController.class)
@Import({SecurityConfig.class, ComplaintExceptionHandler.class})
class ComplaintApiContractTest {

    @Autowired MockMvc mvc;
    @MockitoBean ComplaintOperations complaints;
    @MockitoBean JwtDecoder jwtDecoder; // satisfies the resource-server wiring; jwt() post-processor bypasses it

    /** An authenticated request by a member of the Complaints Handler group, with a username for attribution. */
    private static JwtRequestPostProcessor handler() {
        return jwt()
                .jwt(j -> j.subject("sub-1").claim("preferred_username", "agent.kanmegnea"))
                .authorities(new SimpleGrantedAuthority("GROUP_Complaints Handler"));
    }

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Test
    void register_returns_201_with_the_new_id_and_location() throws Exception {
        when(complaints.register(any(), any(), any(), any(), any())).thenReturn(ID);

        mvc.perform(post("/api/complaints").with(handler())
                        .contentType("application/json")
                        .content("{\"channel\":\"EMAIL\",\"subjectRef\":\"POL-1\",\"summary\":\"overcharged\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/complaints/" + ID))
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    void get_returns_the_summary_never_the_entity() throws Exception {
        when(complaints.get(ID)).thenReturn(new ComplaintSummary(ID, "EMAIL", "POL-1", "overcharged",
                ComplaintStatus.REGISTERED.name(), Instant.parse("2026-11-13T10:15:30Z"), null, null, null));

        mvc.perform(get("/api/complaints/{id}", ID).with(handler()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.channel").value("EMAIL"));
    }

    @Test
    void unknown_id_is_404() throws Exception {
        when(complaints.get(ID)).thenThrow(new ComplaintNotFoundException(ID));
        mvc.perform(get("/api/complaints/{id}", ID).with(handler()))
                .andExpect(status().isNotFound());
    }

    @Test
    void an_illegal_transition_is_409() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalComplaintTransitionException(
                        ComplaintStatus.REGISTERED, ComplaintStatus.ASSIGNED))
                .when(complaints).resolve(any(), any(), any());
        mvc.perform(post("/api/complaints/{id}/resolve", ID).with(handler())
                        .contentType("application/json").content("{\"resolutionNote\":\"x\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalid_input_is_422() throws Exception {
        when(complaints.register(any(), any(), any(), any(), any()))
                .thenThrow(new InvalidComplaintException("unknown channel: NOPE"));
        mvc.perform(post("/api/complaints").with(handler())
                        .contentType("application/json")
                        .content("{\"channel\":\"NOPE\",\"summary\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void no_token_is_401() throws Exception {
        mvc.perform(get("/api/complaints/{id}", ID)).andExpect(status().isUnauthorized());
    }

    @Test
    void a_token_without_the_group_is_403() throws Exception {
        mvc.perform(get("/api/complaints/{id}", ID).with(jwt())) // authenticated, but no GROUP_ authority
                .andExpect(status().isForbidden());
    }

    @Test
    void malformed_json_is_4xx_not_5xx() throws Exception {
        mvc.perform(post("/api/complaints").with(handler())
                        .contentType("application/json").content("{ not json"))
                .andExpect(status().isBadRequest());
    }
}
