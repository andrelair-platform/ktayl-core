package com.ktayl.core.billing.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktayl.core.billing.persistence.IngestedPolicyEntity;
import com.ktayl.core.billing.persistence.IngestedPolicyRepository;
import com.ktayl.core.billing.persistence.InstallmentEntity;
import com.ktayl.core.billing.persistence.InstallmentRepository;
import com.ktayl.core.billing.persistence.InvoiceEntity;
import com.ktayl.core.billing.persistence.InvoiceRepository;
import com.ktayl.core.billing.persistence.PaymentEntity;
import com.ktayl.core.billing.persistence.PaymentRepository;
import com.ktayl.core.billing.persistence.WebhookEventRepository;
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
 * L2 — the webhook capture against a REAL Postgres + the REAL Flyway schema (V1–V7). Proves the committed
 * state transition (payment→succeeded, installment→paid, invoice→settled when ALL paid), idempotent replay,
 * and the park-not-drop behaviour — against actual constraints/FKs, not mocks.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(PaymentCaptureService.class)
class PaymentCaptureIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired IngestedPolicyRepository ingested;
    @Autowired InvoiceRepository invoices;
    @Autowired InstallmentRepository installments;
    @Autowired PaymentRepository payments;
    @Autowired WebhookEventRepository webhookEvents;
    @Autowired PaymentCaptureService capture;

    /** Seed policy→invoice→N installments→a pending payment on installment #1. Returns the intent id. */
    private String seed(String policy, int installmentCount) {
        ingested.save(new IngestedPolicyEntity(policy, 120_000L, "EUR", "MARINE",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), "SUB", "Q", "ingested", Instant.now()));
        UUID invId = UUID.randomUUID();
        invoices.save(new InvoiceEntity(invId, policy, "EUR", 120_000L, "issued", Instant.now()));
        UUID firstInstallment = null;
        for (int seq = 1; seq <= installmentCount; seq++) {
            UUID instId = UUID.randomUUID();
            if (seq == 1) firstInstallment = instId;
            installments.save(new InstallmentEntity(instId, invId, seq, LocalDate.of(2026, seq, 1),
                    120_000L / installmentCount, "open"));
        }
        String intentId = "pi_" + policy;
        payments.save(new PaymentEntity(UUID.randomUUID(), firstInstallment, "key-" + policy, intentId,
                120_000L / installmentCount, "EUR", "pending", Instant.now()));
        return intentId;
    }

    @Test
    void captures_succeeded_marks_paid_settles_and_is_idempotent() {
        String intent = seed("POL-SINGLE", 1);

        capture.capturePaymentSucceeded("evt_1", "payment_intent.succeeded", intent);

        PaymentEntity payment = payments.findByPspIntentId(intent).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo("succeeded");
        assertThat(installments.findById(payment.getInstallmentId()).orElseThrow().isPaid()).isTrue();
        assertThat(invoices.findByPolicyNumber("POL-SINGLE").orElseThrow().getStatus())
                .as("single installment paid → invoice settled").isEqualTo("settled");
        assertThat(webhookEvents.findById("evt_1").orElseThrow().getProcessedAt()).isNotNull();

        // idempotent replay of the same event — no change, no duplicate effect
        capture.capturePaymentSucceeded("evt_1", "payment_intent.succeeded", intent);
        assertThat(payments.findByInstallmentId(payment.getInstallmentId())).hasSize(1);
    }

    @Test
    void does_not_settle_the_invoice_while_other_installments_remain_open() {
        String intent = seed("POL-MULTI", 2);

        capture.capturePaymentSucceeded("evt_2", "payment_intent.succeeded", intent);

        assertThat(invoices.findByPolicyNumber("POL-MULTI").orElseThrow().getStatus())
                .as("one of two installments paid → invoice stays issued").isEqualTo("issued");
    }

    @Test
    void parks_a_webhook_that_arrives_before_its_payment_record() {
        // no seed → no payment for this intent
        capture.capturePaymentSucceeded("evt_orphan", "payment_intent.succeeded", "pi_orphan");

        var parked = webhookEvents.findById("evt_orphan").orElseThrow();
        assertThat(parked.getProcessedAt()).as("parked, not dropped").isNull();
    }
}
