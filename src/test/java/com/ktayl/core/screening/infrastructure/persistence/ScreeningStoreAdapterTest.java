package com.ktayl.core.screening.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktayl.core.screening.domain.AlertView;
import com.ktayl.core.screening.domain.Bucket;
import com.ktayl.core.screening.domain.CaseDecision;
import com.ktayl.core.screening.domain.CaseStatus;
import com.ktayl.core.screening.domain.Hit;
import com.ktayl.core.screening.domain.Party;
import com.ktayl.core.screening.domain.Risk;
import com.ktayl.core.screening.domain.ScreeningAuditLog;
import com.ktayl.core.screening.domain.ScreeningRecord;
import com.ktayl.core.screening.domain.ScreeningStore;
import com.ktayl.core.screening.domain.Verdict;
import com.ktayl.core.screening.domain.Vigilance;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
 * L2 — the screening persistence adapters against a REAL Postgres + the REAL Flyway migrations (V1–V11), not
 * mocks. Proves the entity↔domain mapping round-trips, the {@code screening} schema/columns match the JPA
 * model under {@code ddl-auto=validate}, the hit→alert fan-out + the HITL case persist, idempotency by
 * party/gate/day, and that the append-only audit row is INSERTable. Runs in CI (needs Docker).
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({ScreeningStoreAdapter.class, ScreeningAuditAdapter.class, ScreeningStoreAdapterTest.FixedClock.class})
class ScreeningStoreAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-16T10:15:30Z"), ZoneOffset.UTC);
        }
    }

    @Autowired ScreeningStore store;      // the adapter, injected as the domain port
    @Autowired ScreeningAuditLog audit;   // the audit adapter, injected as the domain port

    private static final Instant NOW = Instant.parse("2026-11-16T10:15:30Z");

    @Test
    void round_trips_a_party_through_the_real_schema() {
        Party saved = store.saveParty(new Party(null, "EXT-7", "Jane Roe",
                LocalDate.parse("1980-01-01"), "FR", "FR", "{\"passport\":\"X\"}", null));

        assertThat(saved.id()).isNotNull();
        Party found = store.findParty(saved.id()).orElseThrow();
        assertThat(found.externalRef()).isEqualTo("EXT-7");
        assertThat(found.name()).isEqualTo("Jane Roe");
        assertThat(found.nationality()).isEqualTo("FR");
        assertThat(store.findPartyByExternalRef("EXT-7")).isPresent();
    }

    @Test
    void persists_a_hold_screening_with_its_alert_and_finds_it() {
        Party party = store.saveParty(new Party(null, "EXT-8", "John Doe", null, "FR", "FR", null, null));
        Hit hit = new Hit(Bucket.SANCTION, 92, "OFAC SDN — John Doe");

        ScreeningRecord record = store.saveScreening(party.id(), "intake", NOW, "20261116",
                Verdict.HOLD, List.of(hit));

        assertThat(record.alertIds()).hasSize(1);
        assertThat(store.findLatestStatus(party.id())).contains(Verdict.HOLD);

        // the alert is readable, joined to its party, with no case yet (undecided)
        UUID alertId = record.alertIds().get(0);
        AlertView view = store.findAlert(alertId).orElseThrow();
        assertThat(view.partyId()).isEqualTo(party.id());
        assertThat(view.bucket()).isEqualTo(Bucket.SANCTION);
        assertThat(view.score()).isEqualTo(92);
        assertThat(view.caseStatus()).isNull();
    }

    @Test
    void is_idempotent_by_party_gate_day() {
        Party party = store.saveParty(new Party(null, "EXT-9", "Repeat Party", null, "FR", "FR", null, null));
        store.saveScreening(party.id(), "intake", NOW, "20261116", Verdict.ALLOW, List.of());

        Optional<ScreeningRecord> prior = store.findScreening(party.id(), "intake",
                LocalDate.parse("2026-11-16"));
        assertThat(prior).isPresent();
        assertThat(prior.get().verdict()).isEqualTo(Verdict.ALLOW);
        // a different gate the same day is not a hit
        assertThat(store.findScreening(party.id(), "bind", LocalDate.parse("2026-11-16"))).isEmpty();
    }

    @Test
    void saves_a_hitl_case_decision_and_lists_the_alert_as_decided() {
        Party party = store.saveParty(new Party(null, "EXT-10", "John Doe", null, "FR", "FR", null, null));
        ScreeningRecord record = store.saveScreening(party.id(), "intake", NOW, "20261116",
                Verdict.HOLD, List.of(new Hit(Bucket.SANCTION, 95, "OFAC SDN")));
        UUID alertId = record.alertIds().get(0);

        store.saveCase(new CaseDecision(alertId, CaseStatus.REF, Risk.STRONG, Vigilance.ENHANCED,
                "officer.sophie", "confirmed match", NOW));

        AlertView decided = store.findAlert(alertId).orElseThrow();
        assertThat(decided.caseStatus()).isEqualTo(CaseStatus.REF);

        // the status filter finds it as REF, and not as PENDING
        List<AlertView> refs = store.listAlerts(CaseStatus.REF, null, null, 20);
        assertThat(refs).extracting(AlertView::alertId).contains(alertId);
        assertThat(store.listAlerts(CaseStatus.PENDING, null, null, 20))
                .extracting(AlertView::alertId).doesNotContain(alertId);
    }

    @Test
    void appends_an_immutable_audit_row() {
        Party party = store.saveParty(new Party(null, "EXT-11", "John Doe", null, "FR", "FR", null, null));

        // append-only INSERT must succeed against the real screening_audit table (bigint IDENTITY)
        audit.record(party.id(), "SCREEN", "officer.sophie", "20261116", "gate=intake verdict=ALLOW hits=0");
        audit.record(party.id(), "DECIDE", "officer.sophie", null, "alert decision");
        // no assertion beyond "the INSERTs did not throw" — the audit port is write-only by design
    }
}
