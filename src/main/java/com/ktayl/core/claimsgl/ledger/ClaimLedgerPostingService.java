package com.ktayl.core.claimsgl.ledger;

import com.ktayl.core.claimsgl.persistence.ClaimLedgerOutboxEntity;
import com.ktayl.core.claimsgl.persistence.ClaimLedgerOutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posts a single claims-GL outbox row to the ledger (ADR-021). Each row is handled in its OWN transaction
 * ({@code REQUIRES_NEW}) so one failure never rolls back another row's success. Idempotent: an already-posted
 * row is skipped, and the {@link ClaimLedgerClient} is itself idempotent by the outbox id — so a crash
 * between the ERPNext post and the DB mark-posted re-posts with the same ref (→ the same JE), never a duplicate.
 */
@Service
public class ClaimLedgerPostingService {

    private static final Logger log = LoggerFactory.getLogger(ClaimLedgerPostingService.class);

    private final ClaimLedgerOutboxRepository outbox;
    private final ClaimJournalEntryFactory jeFactory;
    private final ObjectProvider<ClaimLedgerClient> ledger;

    public ClaimLedgerPostingService(ClaimLedgerOutboxRepository outbox, ClaimJournalEntryFactory jeFactory,
            ObjectProvider<ClaimLedgerClient> ledger) {
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
        ClaimLedgerOutboxEntity row = outbox.findById(outboxId).orElse(null);
        if (row == null || "posted".equals(row.getStatus())) {
            return;
        }
        ClaimLedgerClient client = ledger.getIfAvailable();
        if (client == null) {
            return; // not wired — leave pending
        }
        JournalEntry je = jeFactory.from(row);
        String ref = client.postJournalEntry(je, row.getId().toString()); // throws on a GL failure → tx rolls back
        row.markPosted(ref, Instant.now());
        log.info("posted claims GL {} {} → JE {}", row.getEventType(), row.getReference(), ref);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID outboxId, String error) {
        outbox.findById(outboxId).ifPresent(r -> r.recordFailure(error));
    }
}
