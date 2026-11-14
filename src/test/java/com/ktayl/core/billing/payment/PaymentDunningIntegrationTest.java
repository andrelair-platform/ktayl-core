package com.ktayl.core.billing.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktayl.core.billing.persistence.DunningCaseEntity;
import com.ktayl.core.billing.persistence.DunningCaseRepository;
import com.ktayl.core.billing.persistence.IngestedPolicyEntity;
import com.ktayl.core.billing.persistence.IngestedPolicyRepository;
import com.ktayl.core.billing.persistence.InstallmentEntity;
import com.ktayl.core.billing.persistence.InstallmentRepository;
import com.ktayl.core.billing.persistence.InvoiceEntity;
import com.ktayl.core.billing.persistence.InvoiceRepository;
import com.ktayl.core.billing.persistence.PaymentEntity;
import com.ktayl.core.billing.persistence.PaymentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
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
 * L2 — the non-payment path against a REAL Postgres (BILL-013c): a failed collection marks the payment
 * failed, keeps the installment OPEN, and opens/increments the dunning case; at the lapse threshold the
 * case becomes {@code lapse_pending}; a later success resolves it; and the webhook idempotency holds.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "billing.dunning.lapse-threshold=3"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({PaymentFailureService.class, PaymentCaptureService.class})
class PaymentDunningIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired IngestedPolicyRepository ingested;
    @Autowired InvoiceRepository invoices;
    @Autowired InstallmentRepository installments;
    @Autowired PaymentRepository payments;
    @Autowired DunningCaseRepository dunning;
    @Autowired PaymentFailureService failures;
    @Autowired PaymentCaptureService capture;

    private record Seed(String policy, UUID installmentId, String intent) {}

    private Seed seed(String policy) {
        ingested.save(new IngestedPolicyEntity(policy, 120_000L, "EUR", "MARINE",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), "S", "Q", "ingested", Instant.now()));
        UUID invId = UUID.randomUUID();
        invoices.save(new InvoiceEntity(invId, policy, "EUR", 120_000L, "issued", Instant.now()));
        UUID instId = UUID.randomUUID();
        installments.save(new InstallmentEntity(instId, invId, 1, LocalDate.of(2026, 1, 1), 120_000L, "open"));
        String intent = "pi_" + policy;
        payments.save(new PaymentEntity(UUID.randomUUID(), instId, "key-" + policy, intent, 120_000L, "EUR", "pending", Instant.now()));
        return new Seed(policy, instId, intent);
    }

    @Test
    void a_failed_payment_opens_dunning_keeps_the_installment_open_and_lapses_at_threshold() {
        Seed s = seed("POL-FAIL");

        failures.handlePaymentFailed("evt-f1", "payment_intent.payment_failed", s.intent(), "insufficient_funds");
        var d = dunning.findByInstallmentId(s.installmentId()).orElseThrow();
        assertThat(d.getStatus()).isEqualTo(DunningCaseEntity.OPEN);
        assertThat(d.getAttempts()).isEqualTo(1);
        assertThat(payments.findByPspIntentId(s.intent()).orElseThrow().getStatus()).isEqualTo("failed");
        assertThat(installments.findById(s.installmentId()).orElseThrow().getStatus())
                .as("installment stays open — premium still owed").isEqualTo("open");

        failures.handlePaymentFailed("evt-f2", "payment_intent.payment_failed", s.intent(), "insufficient_funds");
        failures.handlePaymentFailed("evt-f3", "payment_intent.payment_failed", s.intent(), "insufficient_funds");
        d = dunning.findByInstallmentId(s.installmentId()).orElseThrow();
        assertThat(d.getAttempts()).isEqualTo(3);
        assertThat(d.getStatus()).as("3rd failure crosses the lapse threshold").isEqualTo(DunningCaseEntity.LAPSE_PENDING);
    }

    @Test
    void webhook_idempotency_a_replayed_failure_does_not_double_count() {
        Seed s = seed("POL-DUP");
        failures.handlePaymentFailed("evt-dup", "payment_intent.payment_failed", s.intent(), "nsf");
        failures.handlePaymentFailed("evt-dup", "payment_intent.payment_failed", s.intent(), "nsf"); // replay
        assertThat(dunning.findByInstallmentId(s.installmentId()).orElseThrow().getAttempts()).isEqualTo(1);
    }

    @Test
    void a_later_success_resolves_the_dunning_case() {
        Seed s = seed("POL-RECOVER");
        failures.handlePaymentFailed("evt-r1", "payment_intent.payment_failed", s.intent(), "nsf");
        assertThat(dunning.findByInstallmentId(s.installmentId()).orElseThrow().getStatus()).isEqualTo(DunningCaseEntity.OPEN);

        // the retry clears (same intent for the test) → captured → installment paid + dunning resolved
        capture.capturePaymentSucceeded("evt-ok", "payment_intent.succeeded", s.intent());
        assertThat(installments.findById(s.installmentId()).orElseThrow().isPaid()).isTrue();
        assertThat(dunning.findByInstallmentId(s.installmentId()).orElseThrow().getStatus()).isEqualTo(DunningCaseEntity.RESOLVED);
    }

    @Test
    void a_failure_webhook_before_its_payment_is_parked_not_dropped() {
        failures.handlePaymentFailed("evt-orphan", "payment_intent.payment_failed", "pi_orphan", "nsf");
        // no exception + no dunning case created (nothing to dun) — the event is parked for reconciliation
        assertThat(dunning.count()).isZero();
    }
}
