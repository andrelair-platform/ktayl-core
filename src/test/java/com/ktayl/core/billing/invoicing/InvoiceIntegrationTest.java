package com.ktayl.core.billing.invoicing;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktayl.core.billing.persistence.IngestedPolicyEntity;
import com.ktayl.core.billing.persistence.IngestedPolicyRepository;
import com.ktayl.core.billing.persistence.InstallmentEntity;
import com.ktayl.core.billing.persistence.InstallmentRepository;
import com.ktayl.core.billing.persistence.InvoiceEntity;
import com.ktayl.core.billing.persistence.InvoiceRepository;
import java.time.Instant;
import java.time.LocalDate;
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
 * L2 — the invoice flow against a REAL Postgres + the REAL Flyway migrations (V1–V3), not mocks. Proves
 * the schema/constraints/JPA mapping + that the money reconciles end-to-end. Runs in CI (Docker present).
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(InvoiceService.class)
class InvoiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired IngestedPolicyRepository ingested;
    @Autowired InvoiceRepository invoices;
    @Autowired InstallmentRepository installments;
    @Autowired InvoiceService service;

    @Test
    void raises_a_reconciling_invoice_against_the_real_schema_and_is_idempotent() {
        ingested.save(new IngestedPolicyEntity("POL-INT", 100_000L, "EUR", "PROPERTY",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), "SUB", "Q", "ingested", Instant.now()));

        var result = service.raiseFor("POL-INT", 3);
        assertThat(result.created()).isTrue();

        InvoiceEntity inv = invoices.findByPolicyNumber("POL-INT").orElseThrow();
        var items = installments.findByInvoiceIdOrderBySeq(inv.getId());
        assertThat(items).hasSize(3);
        assertThat(items.stream().mapToLong(InstallmentEntity::getAmountMinor).sum())
                .isEqualTo(inv.getTotalMinor()).isEqualTo(100_000L); // reconciles on the real DB

        // idempotent on the real UNIQUE(policy_number) backstop
        assertThat(service.raiseFor("POL-INT", 3).created()).isFalse();
        assertThat(invoices.count()).isEqualTo(1);
    }
}
