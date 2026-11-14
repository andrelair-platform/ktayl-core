package com.ktayl.core.billing.ledger;

import com.ktayl.core.billing.persistence.LedgerOutboxEntity;
import com.ktayl.core.billing.persistence.LedgerOutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posts a single outbox row to the GL (BILL-014). Each row is handled in its OWN transaction
 * ({@code REQUIRES_NEW}) so one failure never rolls back another row's success, and the drainer can keep
 * going. Idempotent: an already-posted row is skipped, and the {@link LedgerClient} is itself idempotent by
 * the outbox id — so a crash between the ERPNext post and the DB mark-posted re-posts with the same ref
 * (→ the same JE), never a duplicate.
 */
@Service
public class LedgerPostingService {

    private static final Logger log = LoggerFactory.getLogger(LedgerPostingService.class);

    private final LedgerOutboxRepository outbox;
    private final JournalEntryFactory jeFactory;
    private final ObjectProvider<LedgerClient> ledger;

    public LedgerPostingService(LedgerOutboxRepository outbox, JournalEntryFactory jeFactory,
            ObjectProvider<LedgerClient> ledger) {
        this.outbox = outbox;
        this.jeFactory = jeFactory;
        this.ledger = ledger;
    }

    /** True if a ledger client is wired (ERPNext configured); otherwise the outbox just accumulates. */
    public boolean ledgerAvailable() {
        return ledger.getIfAvailable() != null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void post(UUID outboxId) {
        LedgerOutboxEntity row = outbox.findById(outboxId).orElse(null);
        if (row == null || "posted".equals(row.getStatus())) {
            return;
        }
        LedgerClient client = ledger.getIfAvailable();
        if (client == null) {
            return; // not wired — leave pending
        }
        JournalEntry je = jeFactory.from(row);
        String ref = client.postJournalEntry(je, row.getId().toString()); // throws on a GL failure → tx rolls back
        row.markPosted(ref, Instant.now());
        log.info("posted GL {} {} → JE {}", row.getEventType(), row.getReference(), ref);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID outboxId, String error) {
        outbox.findById(outboxId).ifPresent(r -> r.recordFailure(error));
    }
}
