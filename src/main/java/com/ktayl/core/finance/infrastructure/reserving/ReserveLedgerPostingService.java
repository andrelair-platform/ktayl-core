package com.ktayl.core.finance.infrastructure.reserving;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posts a single reserve-movement outbox row to the ledger (FIN-03, ADR-022). Each row is handled in its OWN
 * transaction ({@code REQUIRES_NEW}) so one failure never rolls back another row's success. Idempotent: an
 * already-posted row is skipped, and the {@link ReserveLedgerClient} is itself idempotent by the outbox id.
 */
@Service
public class ReserveLedgerPostingService {

    private static final Logger log = LoggerFactory.getLogger(ReserveLedgerPostingService.class);

    private final ReserveMovementOutboxRepository outbox;
    private final ReserveJournalEntryFactory jeFactory;
    private final ObjectProvider<ReserveLedgerClient> ledger;

    public ReserveLedgerPostingService(ReserveMovementOutboxRepository outbox, ReserveJournalEntryFactory jeFactory,
            ObjectProvider<ReserveLedgerClient> ledger) {
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
        ReserveMovementOutboxEntity row = outbox.findById(outboxId).orElse(null);
        if (row == null || "posted".equals(row.getStatus())) {
            return;
        }
        ReserveLedgerClient client = ledger.getIfAvailable();
        if (client == null) {
            return; // not wired — leave pending
        }
        ReserveJournalEntry je = jeFactory.from(row);
        String ref = client.postJournalEntry(je, row.getId().toString()); // throws on failure → tx rolls back
        row.markPosted(ref, Instant.now());
        log.info("posted IBNR reserve movement {} ({} {}) → JE {}",
                row.getReference(), row.getAmountMinor(), row.getCurrency(), ref);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID outboxId, String error) {
        outbox.findById(outboxId).ifPresent(r -> r.recordFailure(error));
    }
}
