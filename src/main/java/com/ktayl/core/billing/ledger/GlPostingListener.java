package com.ktayl.core.billing.ledger;

import com.ktayl.core.billing.invoicing.PremiumInvoiced;
import com.ktayl.core.billing.payment.PaymentCaptured;
import com.ktayl.core.billing.persistence.LedgerOutboxEntity;
import com.ktayl.core.billing.persistence.LedgerOutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Writes a GL posting into the outbox (BILL-014) when a billing fact occurs. This is a plain
 * {@link EventListener} — synchronous, running INSIDE the publishing transaction — <b>not</b> an
 * {@code @TransactionalEventListener(AFTER_COMMIT)}. That is the whole point of a transactional outbox: the
 * outbox row must commit <i>atomically</i> with the business state (invoice raised / payment captured), so a
 * GL intent is recorded iff the business fact is — never a half-post, and never a lost post. (Contrast the
 * invoice auto-chain, which is deliberately AFTER_COMMIT+REQUIRES_NEW because it is best-effort decoupled.)
 *
 * <p>The actual ERPNext post happens later, out of band, in {@code LedgerOutboxDrainer} — so a GL outage
 * never blocks invoicing or payment capture.
 */
@Component
public class GlPostingListener {

    private static final Logger log = LoggerFactory.getLogger(GlPostingListener.class);

    private final LedgerOutboxRepository outbox;

    public GlPostingListener(LedgerOutboxRepository outbox) {
        this.outbox = outbox;
    }

    @EventListener
    public void onPremiumInvoiced(PremiumInvoiced e) {
        // amount = GROSS (the receivable + premium register key off gross); commission drives the 3rd JE leg.
        enqueue(JournalEntryFactory.PREMIUM_INVOICED, e.policyNumber(), e.totalMinor(), e.commissionMinor(), e.currency());
    }

    @EventListener
    public void onPaymentCaptured(PaymentCaptured e) {
        enqueue(JournalEntryFactory.PAYMENT_CAPTURED, e.paymentId().toString(), e.amountMinor(), 0L, e.currency());
    }

    private void enqueue(String eventType, String reference, long amountMinor, long commissionMinor, String currency) {
        // idempotent at the row level (UNIQUE(event_type, reference)) — a replayed event never double-queues
        if (outbox.existsByEventTypeAndReference(eventType, reference)) {
            return;
        }
        outbox.save(new LedgerOutboxEntity(UUID.randomUUID(), eventType, reference, amountMinor, commissionMinor,
                currency, Instant.now()));
        log.debug("queued GL posting {} {} ({} {} eurocents, commission {})", eventType, reference, amountMinor, currency, commissionMinor);
    }
}
