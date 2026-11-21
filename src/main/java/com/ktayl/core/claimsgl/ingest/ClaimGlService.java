package com.ktayl.core.claimsgl.ingest;

import com.ktayl.core.claimsgl.ledger.ClaimJournalEntryFactory;
import com.ktayl.core.claimsgl.persistence.ClaimLedgerOutboxEntity;
import com.ktayl.core.claimsgl.persistence.ClaimLedgerOutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a validated {@link ClaimFinancialEvent} into a claims GL outbox row (ADR-021) — the transactional
 * outbox write. <b>Idempotent by {@code event_id}</b> (NATS is at-least-once): a redelivered event is a
 * no-op, backed by the outbox {@code UNIQUE(reference)} at the DB level. The actual ERPNext post happens
 * out of band in the drainer, so a GL outage never blocks the consumer.
 *
 * <p>Maps the event {@code type} to the outbox {@code event_type} the JE factory understands:
 * {@code reserve-set → RESERVE_MOVEMENT} (amount = the signed movement), {@code payment-executed →
 * CLAIM_PAYMENT} (amount &gt; 0).
 */
@Service
public class ClaimGlService {

    private static final Logger log = LoggerFactory.getLogger(ClaimGlService.class);

    private final ClaimLedgerOutboxRepository outbox;

    public ClaimGlService(ClaimLedgerOutboxRepository outbox) {
        this.outbox = outbox;
    }

    /** @return true if a new outbox row was written, false if it was an idempotent re-delivery. */
    @Transactional
    public boolean record(ClaimFinancialEvent event) {
        event.validate();
        if (outbox.existsByReference(event.eventId())) {
            log.debug("claim financial event {} already recorded — idempotent", event.eventId());
            return false;
        }
        String eventType = switch (event.type()) {
            case ClaimFinancialEvent.TYPE_RESERVE_SET -> ClaimJournalEntryFactory.RESERVE_MOVEMENT;
            case ClaimFinancialEvent.TYPE_PAYMENT_EXECUTED -> ClaimJournalEntryFactory.CLAIM_PAYMENT;
            default -> throw new IllegalArgumentException("unknown claim financial event type: " + event.type());
        };
        outbox.save(new ClaimLedgerOutboxEntity(UUID.randomUUID(), eventType, event.eventId(),
                event.claimNumber(), event.amountMinor(), event.currency(), Instant.now()));
        log.info("queued claims GL {} for claim {} ({} {} eurocents, ref {})",
                eventType, event.claimNumber(), event.amountMinor(), event.currency(), event.eventId());
        return true;
    }
}
