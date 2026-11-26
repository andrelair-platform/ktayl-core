package com.ktayl.core.claimsgl.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * The claim <b>financial</b> event (subject {@code insurance.claims.financial}) — this module's pinned view
 * of what {@code ktayl-claims} emits when a reserve moves or a loss payment executes (ADR-021). Like
 * billing's {@code BoundRiskEvent}, the shape is the consumer contract (snake_case keys, {@code amount_minor}
 * is integer eurocents, {@code occurred_on} is {@code YYYY-MM-DD}); an L1 contract test guards it.
 *
 * @param eventId      stable id per financial movement — the idempotency + JE-remark key (NATS is
 *                     at-least-once, so a redelivery must dedupe on this)
 * @param type         {@code "reserve-set"} (a reserve movement) or {@code "payment-executed"} (a loss payment)
 * @param claimNumber  the claim this posting belongs to
 * @param policyNumber the covering policy (provenance; nullable)
 * @param amountMinor  for {@code reserve-set}: the reserve MOVEMENT in eurocents, <b>signed</b> (+increase /
 *                     −downward adjustment); for {@code payment-executed}: the payment amount (&gt; 0)
 * @param currency     ISO-4217 (EUR)
 * @param occurredOn   the business date of the movement
 */
public record ClaimFinancialEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("type") String type,
        @JsonProperty("claim_number") String claimNumber,
        @JsonProperty("policy_number") String policyNumber,
        @JsonProperty("amount_minor") long amountMinor,
        @JsonProperty("currency") String currency,
        @JsonProperty("occurred_on") LocalDate occurredOn) {

    public static final String TYPE_RESERVE_SET = "reserve-set";
    public static final String TYPE_PAYMENT_EXECUTED = "payment-executed";

    /** Validate the fields this module relies on; a poison message is TERMed by the consumer, not retried. */
    public void validate() {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("event_id is required");
        if (claimNumber == null || claimNumber.isBlank()) throw new IllegalArgumentException("claim_number is required");
        if (currency == null || currency.length() != 3) throw new IllegalArgumentException("currency must be ISO-4217");
        if (occurredOn == null) throw new IllegalArgumentException("occurred_on is required");
        validateAmountForType();
    }

    /** The per-type amount rule (reserve-set must be non-zero; payment-executed must be > 0). */
    private void validateAmountForType() {
        switch (type == null ? "" : type) {
            case TYPE_RESERVE_SET -> {
                if (amountMinor == 0) throw new IllegalArgumentException("reserve-set movement must be non-zero");
            }
            case TYPE_PAYMENT_EXECUTED -> {
                if (amountMinor <= 0) throw new IllegalArgumentException("payment-executed amount must be > 0");
            }
            default -> throw new IllegalArgumentException("unknown claim financial event type: " + type);
        }
    }
}
