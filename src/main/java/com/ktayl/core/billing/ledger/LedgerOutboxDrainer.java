package com.ktayl.core.billing.ledger;

import com.ktayl.core.billing.persistence.LedgerOutboxEntity;
import com.ktayl.core.billing.persistence.LedgerOutboxRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the GL outbox to ERPNext on a schedule (BILL-014). Reads {@code pending} postings and hands each to
 * {@link LedgerPostingService} (one tx per row). A GL outage just leaves rows pending → retried next tick;
 * billing is never blocked. If no ledger client is wired, it no-ops (the outbox accumulates until it is).
 */
@Component
public class LedgerOutboxDrainer {

    private static final Logger log = LoggerFactory.getLogger(LedgerOutboxDrainer.class);
    private static final int BATCH = 50;

    private final LedgerOutboxRepository outbox;
    private final LedgerPostingService posting;

    public LedgerOutboxDrainer(LedgerOutboxRepository outbox, LedgerPostingService posting) {
        this.outbox = outbox;
        this.posting = posting;
    }

    @Scheduled(fixedDelayString = "${ledger.drain-interval-ms:30000}", initialDelayString = "${ledger.drain-initial-ms:20000}")
    public void drain() {
        if (!posting.ledgerAvailable()) {
            return; // ERPNext not configured — nothing to drain to yet
        }
        List<LedgerOutboxEntity> batch = outbox.findByStatusOrderByCreatedAt("pending", Limit.of(BATCH));
        if (batch.isEmpty()) {
            return;
        }
        int ok = 0;
        for (LedgerOutboxEntity row : batch) {
            UUID id = row.getId();
            try {
                posting.post(id);
                ok++;
            } catch (Exception e) {
                log.warn("GL post failed for {} ({}), will retry: {}", row.getEventType(), row.getReference(), e.getMessage());
                posting.recordFailure(id, e.getMessage());
            }
        }
        log.debug("GL outbox drain: {}/{} posted", ok, batch.size());
    }
}
