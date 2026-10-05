package com.ktayl.core.billing.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * L3 CONTRACT — pins {@link BoundRiskEvent} to the EXACT payload Underwriting publishes to
 * {@code insurance.underwriting.bound-risk} (ktayl-underwriting {@code app/bind/service.py}). This is the
 * mock-discipline guard: if UW changes a field name/shape, this fails pre-merge instead of silently
 * dropping ingests live. Note the two things that bit other services: {@code premium_minor} MUST stay an
 * integer (eurocents, never a float) and the dates are {@code YYYY-MM-DD} strings → {@link LocalDate}.
 */
class BoundRiskEventContractTest {

    // Verbatim shape emitted by UW's bind service (snake_case keys; date strings; integer premium).
    private static final String UW_BOUND_RISK_PAYLOAD =
            """
            {
              "policy_number": "UW-369188ABE405",
              "submission_id": "SUB-abc123",
              "quote_id": "Q-xyz789",
              "premium_minor": 1234567,
              "currency": "EUR",
              "product_code": "PROPERTY",
              "effective_date": "2026-01-15",
              "expiry_date": "2027-01-15"
            }
            """;

    private final JsonMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @Test
    void deserializes_the_real_underwriting_payload() throws Exception {
        BoundRiskEvent e = mapper.readValue(UW_BOUND_RISK_PAYLOAD, BoundRiskEvent.class);

        assertThat(e.policyNumber()).isEqualTo("UW-369188ABE405");
        assertThat(e.premiumMinor()).isEqualTo(1_234_567L); // eurocents, integer — NOT a float
        assertThat(e.currency()).isEqualTo("EUR");
        assertThat(e.productCode()).isEqualTo("PROPERTY");
        assertThat(e.effectiveDate()).isEqualTo(LocalDate.of(2026, 1, 15)); // YYYY-MM-DD → LocalDate
        assertThat(e.expiryDate()).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(e.submissionId()).isEqualTo("SUB-abc123");
        assertThat(e.quoteId()).isEqualTo("Q-xyz789");
    }
}
