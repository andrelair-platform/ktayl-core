package com.ktayl.core.policy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktayl.core.policy.application.CreatePolicyCommand;
import com.ktayl.core.policy.application.PolicyService;
import com.ktayl.core.policy.domain.Policy;
import com.ktayl.core.policy.domain.PolicyAlreadyExistsException;
import com.ktayl.core.policy.domain.PolicyStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * L2 — the underwriting bind path through the WHOLE module stack against a REAL Postgres + the REAL Flyway
 * migrations (V10 creates the {@code policy} schema). Proves what L1 (pure domain) and mocks cannot: the
 * entity↔domain mapping round-trips against the real columns ({@code ddl-auto=validate}), the DORA-Art.9
 * audit rows (BF-POL-03) are actually written with the right {@code from→to}/actor/reason, the policy_number
 * UNIQUE constraint backstops the 409 idempotency contract, and resolve-by-number returns the live row.
 * Wires {@link PolicyService} over the two real adapters (not mocks). Runs in CI.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({PolicyService.class, PolicyPersistenceAdapter.class, PolicyAuditAdapter.class})
class PolicyBindFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired PolicyService policies;
    @Autowired PolicyAuditJpaRepository auditLog;

    private CreatePolicyCommand cmd(String number) {
        return new CreatePolicyCommand(number, "ACME SA", "MARINE",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
    }

    @Test
    void the_bind_flow_persists_active_and_writes_the_dora_audit_trail() {
        Policy created = policies.create(cmd("POL-BIND-1"), "svc.underwriting");
        assertThat(created.status()).isEqualTo(PolicyStatus.DRAFT);

        policies.submit(created.id(), "svc.underwriting");
        policies.activate(created.id(), "svc.underwriting");

        // re-read from the DB (not the returned aggregate) to prove it was persisted
        assertThat(policies.get(created.id()).status()).isEqualTo(PolicyStatus.ACTIVE);

        // BF-POL-03: one immutable audit row per transition, in order, with actor + from→to
        List<PolicyAuditEntity> trail = auditLog.findAll().stream()
                .filter(a -> a.getPolicyId().equals(created.id()))
                .sorted((a, b) -> a.getAt().compareTo(b.getAt()))
                .toList();
        assertThat(trail).extracting(PolicyAuditEntity::getAction).containsExactly("CREATE", "SUBMIT", "ACTIVATE");
        assertThat(trail).allSatisfy(a -> assertThat(a.getActorId()).isEqualTo("svc.underwriting"));
        assertThat(trail.get(2).getFromStatus()).isEqualTo("SUBMITTED");
        assertThat(trail.get(2).getToStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void a_duplicate_policy_number_is_rejected_and_resolvable() {
        policies.create(cmd("POL-DUP-1"), "svc.underwriting");

        assertThatThrownBy(() -> policies.create(cmd("POL-DUP-1"), "svc.underwriting"))
                .isInstanceOf(PolicyAlreadyExistsException.class);

        // the PAS idempotency contract: the caller resolves the existing one by number
        assertThat(policies.findByPolicyNumber("POL-DUP-1")).isPresent();
        assertThat(policies.findByPolicyNumber("POL-UNKNOWN")).isEmpty();
    }

    @Test
    void cancel_persists_the_terminal_state_with_a_reason_in_the_audit() {
        Policy p = policies.create(cmd("POL-CXL-1"), "svc.underwriting");
        policies.submit(p.id(), "svc.underwriting");
        policies.activate(p.id(), "svc.underwriting");

        policies.cancel(p.id(), "NON_PAYMENT", "agent.kanmegnea");

        assertThat(policies.get(p.id()).status()).isEqualTo(PolicyStatus.CANCELLED);
        PolicyAuditEntity cancel = auditLog.findAll().stream()
                .filter(a -> a.getPolicyId().equals(p.id()) && a.getAction().equals("CANCEL"))
                .findFirst().orElseThrow();
        assertThat(cancel.getReason()).isEqualTo("NON_PAYMENT");
        assertThat(cancel.getActorId()).isEqualTo("agent.kanmegnea");
        assertThat(cancel.getFromStatus()).isEqualTo("ACTIVE");
    }
}
