package com.ktayl.core.submission.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktayl.core.screening.api.ScreeningGate;
import com.ktayl.core.screening.api.ScreeningOutcome;
import com.ktayl.core.submission.api.AddExposureCommand;
import com.ktayl.core.submission.api.RegisterSubmissionCommand;
import com.ktayl.core.submission.api.RouteSubmissionCommand;
import com.ktayl.core.submission.api.SubmissionView;
import com.ktayl.core.submission.api.TriageSubmissionCommand;
import com.ktayl.core.submission.application.SubmissionService;
import com.ktayl.core.submission.domain.SubmissionScreeningHoldException;
import com.ktayl.core.submission.domain.UnderwritingPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * L2 — the submission hub flow through the WHOLE module stack against a REAL Postgres + the REAL Flyway
 * migrations (V12 creates the {@code submission} schema). Proves what L1 (pure domain) and mocks cannot:
 * the entity↔domain mapping round-trips against the real columns ({@code ddl-auto=validate}), the exposure
 * dossier + the append-only triage rows actually persist, the keyset list pages, and the screening HOLD
 * flagged at intake blocks the route. Wires {@link SubmissionService} over the two real adapters (not mocks)
 * with a STUBBED {@link ScreeningGate} (this slice is not about the engine). Runs in CI (needs Docker).
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({SubmissionService.class, SubmissionPersistenceAdapter.class, TriageLogAdapter.class,
        SubmissionFlowIntegrationTest.Stubs.class})
class SubmissionFlowIntegrationTest {

    /** A controllable screening gate + a stubbed underwriting handoff + a fixed clock. */
    @TestConfiguration
    static class Stubs {
        /** Flipped per test via a static holder; ALLOW by default. */
        static volatile ScreeningOutcome nextOutcome = new ScreeningOutcome("ALLOW", null, "test");

        /** The UW submission id the stubbed handoff returns (no real underwriting service in this slice). */
        static final String STUBBED_UW_REF = "11111111-1111-1111-1111-111111111111";

        @Bean
        ScreeningGate screeningGate() {
            return (party, gate, actor) -> nextOutcome;
        }

        /** Stub the underwriting handoff — this slice proves persistence, not the real HTTP adapter. */
        @Bean
        UnderwritingPort underwritingPort() {
            return request -> STUBBED_UW_REF;
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-17T10:15:30Z"), ZoneOffset.UTC);
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired SubmissionService submissions;
    @Autowired SubmissionTriageJpaRepository triageRows;
    @Autowired ExposureItemJpaRepository exposureRows;

    private RegisterSubmissionCommand cmd(String brokerRef) {
        return new RegisterSubmissionCommand(brokerRef, "ACME SA", "MARINE");
    }

    @Test
    void the_full_flow_registers_adds_exposure_triages_and_routes() {
        Stubs.nextOutcome = new ScreeningOutcome("ALLOW", null, "test");

        UUID id = submissions.register(cmd("BRK-FLOW-1"), "agent.kanmegnea");
        assertThat(submissions.findById(id)).get().satisfies(v -> {
            assertThat(v.status()).isEqualTo("RECEIVED");
            assertThat(v.screeningHold()).isFalse();
        });

        UUID exposureId = submissions.addExposure(id, new AddExposureCommand("Rotterdam Terminal", "NL",
                new BigDecimal("12500000.00"), "EUR", "warehouse", "3011AA", "no losses 5y", true),
                "agent.kanmegnea");
        assertThat(exposureRows.findById(exposureId)).get().satisfies(e -> {
            assertThat(e.getSubmissionId()).isEqualTo(id);
            assertThat(e.getTivAmount()).isEqualByComparingTo("12500000.00");
            assertThat(e.getPostcode()).isEqualTo("3011AA");
            assertThat(e.isCatFlag()).isTrue();
        });

        submissions.triage(id, new TriageSubmissionCommand("ROUTE", 90, "IN_APPETITE", "complete"),
                "agent.kanmegnea");
        assertThat(submissions.findById(id)).get().extracting(SubmissionView::status).isEqualTo("TRIAGED");

        // route hands off to the (stubbed) underwriting port → the returned UW ref is persisted
        submissions.route(id, new RouteSubmissionCommand("underwriting"), "agent.kanmegnea");
        SubmissionView routed = submissions.findById(id).orElseThrow();
        assertThat(routed.status()).isEqualTo("ROUTED");
        assertThat(routed.routedTarget()).isEqualTo("underwriting");
        assertThat(routed.uwSubmissionRef()).isEqualTo(Stubs.STUBBED_UW_REF);

        // the append-only triage row was written for this submission
        assertThat(triageRows.findAll()).anySatisfy(t -> {
            assertThat(t.getSubmissionId()).isEqualTo(id);
            assertThat(t.getDecision()).isEqualTo("ROUTE");
            assertThat(t.getActor()).isEqualTo("agent.kanmegnea");
        });
    }

    @Test
    void a_submission_held_at_intake_cannot_be_routed_until_cleared() {
        UUID alert = UUID.randomUUID();
        Stubs.nextOutcome = new ScreeningOutcome("HOLD", alert, null);

        UUID id = submissions.register(cmd("BRK-HOLD-1"), "agent.kanmegnea");
        SubmissionView held = submissions.findById(id).orElseThrow();
        assertThat(held.screeningHold()).isTrue();       // captured but flagged

        // triage is still allowed while held
        submissions.triage(id, new TriageSubmissionCommand("ROUTE", 70, null, null), "agent.kanmegnea");

        // but routing is refused pending the sanctions review
        assertThatThrownBy(() -> submissions.route(id, new RouteSubmissionCommand("underwriting"),
                "agent.kanmegnea"))
                .isInstanceOf(SubmissionScreeningHoldException.class)
                .satisfies(e -> assertThat(((SubmissionScreeningHoldException) e).alertId()).isEqualTo(alert));
        assertThat(submissions.findById(id)).get().extracting(SubmissionView::status).isEqualTo("TRIAGED");

        // after a compliance officer clears the hold, the route proceeds
        submissions.clearScreeningHold(id, "officer.sophie");
        submissions.route(id, new RouteSubmissionCommand("underwriting"), "agent.kanmegnea");
        assertThat(submissions.findById(id)).get().extracting(SubmissionView::status).isEqualTo("ROUTED");
    }

    @Test
    void list_pages_through_in_received_order_with_a_cursor_and_status_filter() {
        Stubs.nextOutcome = new ScreeningOutcome("ALLOW", null, "test");
        for (int i = 0; i < 5; i++) {
            submissions.register(cmd("BRK-PAGE-" + i), "agent.kanmegnea");
        }
        List<SubmissionView> page1 = submissions.list("RECEIVED", null, null, 2);
        assertThat(page1).hasSize(2);
        SubmissionView last = page1.get(1);
        List<SubmissionView> page2 = submissions.list("RECEIVED", last.receivedAt(), last.id(), 2);
        assertThat(page2).hasSize(2);
        assertThat(page2).noneSatisfy(v -> assertThat(v.id()).isEqualTo(last.id()));

        // the status filter excludes non-RECEIVED submissions
        UUID routedId = submissions.register(cmd("BRK-PAGE-ROUTED"), "agent.kanmegnea");
        submissions.triage(routedId, new TriageSubmissionCommand("ROUTE", null, null, null), "agent.kanmegnea");
        submissions.route(routedId, new RouteSubmissionCommand("underwriting"), "agent.kanmegnea");
        assertThat(submissions.list("ROUTED", null, null, 50))
                .anySatisfy(v -> assertThat(v.id()).isEqualTo(routedId))
                .allSatisfy(v -> assertThat(v.status()).isEqualTo("ROUTED"));
    }
}
