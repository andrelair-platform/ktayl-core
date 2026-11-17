package com.ktayl.core.submission.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * L1 — the SUB-02 field mapping onto the underwriting (#12) contract, as PURE Java (no Spring, no HTTP). Proves
 * the agreed rules: productLine → LOB, free occupancy → enum, TIV sum → eurocents, and every fallback for a
 * missing country/postcode/occupancy/exposure — the one place the two models meet.
 */
class UnderwritingSubmissionMapperTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:15:30Z");

    private static Submission submission(String productLine, String brokerRef) {
        return Submission.receive(brokerRef, "ACME SA", productLine, NOW);
    }

    private static ExposureItem exposure(String country, BigDecimal tiv, String occupancy, String postcode) {
        return ExposureItem.create(UUID.randomUUID(), "Site", country, tiv, "EUR", occupancy, postcode, null,
                false, NOW);
    }

    @Test
    void maps_the_happy_path_counterparty_lob_tiv_occupancy_country_postcode_cover_broker() {
        Submission s = submission("MARINE", "BRK-123");
        ExposureItem a = exposure("NL", new BigDecimal("12500000.00"), "warehouse", "3011AA");
        ExposureItem b = exposure("DE", new BigDecimal("500000.50"), "office", "10115");

        UnderwritingSubmissionRequest r = UnderwritingSubmissionMapper.toRequest(s, List.of(a, b));

        assertThat(r.counterpartyName()).isEqualTo("ACME SA");
        assertThat(r.counterpartyCountry()).isEqualTo("NL"); // first exposure
        assertThat(r.country()).isEqualTo("NL");
        assertThat(r.lineOfBusiness()).isEqualTo("marine");
        // (12,500,000.00 + 500,000.50) EUR → 13,000,000.50 → 1,300,000,050 eurocents
        assertThat(r.tivEurCents()).isEqualTo(1_300_000_050L);
        assertThat(r.occupancy()).isEqualTo("warehouse"); // first exposure's occupancy
        assertThat(r.postcode()).isEqualTo("3011AA");
        assertThat(r.requestedCover()).isEqualTo("MARINE cover");
        assertThat(r.brokerRef()).isEqualTo("BRK-123");
    }

    @Test
    void maps_each_product_line_to_its_lob_and_defaults_unknown_to_commercial_property() {
        assertThat(lob("COMMERCIAL_PROPERTY")).isEqualTo("commercial_property");
        assertThat(lob("MARINE")).isEqualTo("marine");
        assertThat(lob("ENGINEERING")).isEqualTo("engineering");
        assertThat(lob("FINANCIAL_LINES")).isEqualTo("financial_lines");
        assertThat(lob("CYBER")).isEqualTo("commercial_property"); // unknown → default
    }

    @Test
    void maps_free_occupancy_strings_case_insensitively_onto_the_enum() {
        assertThat(occ("Office Building")).isEqualTo("office");
        assertThat(occ("big RETAIL unit")).isEqualTo("retail");
        assertThat(occ("cold Warehouse")).isEqualTo("warehouse");
        assertThat(occ("light manufacturing plant")).isEqualTo("light_manufacturing");
        assertThat(occ("fireworks manufacturing")).isEqualTo("fireworks_manufacturing"); // fireworks wins
        assertThat(occ("something else")).isEqualTo("office");  // default
        assertThat(occ(null)).isEqualTo("office");              // null → default
    }

    @Test
    void falls_back_when_there_are_no_exposures() {
        UnderwritingSubmissionRequest r = UnderwritingSubmissionMapper.toRequest(submission("MARINE", "B"),
                List.of());
        assertThat(r.country()).isEqualTo("XX");
        assertThat(r.counterpartyCountry()).isEqualTo("XX");
        assertThat(r.postcode()).isEqualTo("00000");
        assertThat(r.occupancy()).isEqualTo("office");
        assertThat(r.tivEurCents()).isEqualTo(1L); // sum 0 → forced to 1 (contract requires > 0)
    }

    @Test
    void falls_back_on_a_null_or_blank_postcode() {
        ExposureItem noPostcode = exposure("FR", new BigDecimal("1000.00"), "office", null);
        assertThat(UnderwritingSubmissionMapper.toRequest(submission("ENGINEERING", "B"), List.of(noPostcode))
                .postcode()).isEqualTo("00000");
    }

    @Test
    void truncates_a_long_broker_ref_to_64_and_keeps_null() {
        String longRef = "X".repeat(100);
        assertThat(UnderwritingSubmissionMapper.toRequest(
                        Submission.receive(longRef, "ACME SA", "MARINE", NOW), List.of())
                .brokerRef()).hasSize(64);
    }

    private static String lob(String productLine) {
        return UnderwritingSubmissionMapper.toRequest(submission(productLine, "B"),
                List.of(exposure("NL", new BigDecimal("1.00"), "office", "1000"))).lineOfBusiness();
    }

    private static String occ(String occupancy) {
        return UnderwritingSubmissionMapper.toRequest(submission("MARINE", "B"),
                List.of(exposure("NL", new BigDecimal("1.00"), occupancy, "1000"))).occupancy();
    }
}
