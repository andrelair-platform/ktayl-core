package com.ktayl.core.claimsgl.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * L1 contract — pins the wire shape this module consumes (ADR-021). If ktayl-claims ever emits a different
 * key/type for {@code insurance.claims.financial}, this fails pre-merge (the same guard billing has on the
 * bound-risk payload). snake_case keys, {@code amount_minor} integer eurocents, {@code occurred_on} a date.
 */
class ClaimFinancialEventContractTest {

    private final ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @Test
    void deserialises_a_reserve_set_event_from_the_wire() throws Exception {
        String json = """
                {"event_id":"evt-123","type":"reserve-set","claim_number":"CLM-900",
                 "policy_number":"POL-5","amount_minor":500000,"currency":"EUR","occurred_on":"2026-03-01"}
                """;
        ClaimFinancialEvent e = mapper.readValue(json, ClaimFinancialEvent.class);
        assertThat(e.eventId()).isEqualTo("evt-123");
        assertThat(e.type()).isEqualTo(ClaimFinancialEvent.TYPE_RESERVE_SET);
        assertThat(e.claimNumber()).isEqualTo("CLM-900");
        assertThat(e.amountMinor()).isEqualTo(500_000L);
        assertThat(e.currency()).isEqualTo("EUR");
        assertThat(e.occurredOn()).isEqualTo(LocalDate.of(2026, 3, 1));
        e.validate(); // valid
    }

    @Test
    void a_negative_reserve_movement_is_valid_a_zero_one_is_not() {
        assertThat(ev("reserve-set", -50_000)).satisfies(ClaimFinancialEvent::validate);
        assertThatThrownBy(() -> ev("reserve-set", 0).validate())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("non-zero");
    }

    @Test
    void a_payment_must_be_positive() {
        assertThat(ev("payment-executed", 300_000)).satisfies(ClaimFinancialEvent::validate);
        assertThatThrownBy(() -> ev("payment-executed", -1).validate())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("> 0");
    }

    @Test
    void an_unknown_type_is_a_poison_message() {
        assertThatThrownBy(() -> ev("refunded", 10).validate())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown");
    }

    private static ClaimFinancialEvent ev(String type, long amount) {
        return new ClaimFinancialEvent("evt-x", type, "CLM-1", "POL-1", amount, "EUR", LocalDate.of(2026, 1, 1));
    }
}
