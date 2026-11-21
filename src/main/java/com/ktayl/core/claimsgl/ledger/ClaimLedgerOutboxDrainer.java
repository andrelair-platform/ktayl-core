package com.ktayl.core.claimsgl.ledger;

import com.ktayl.core.claimsgl.persistence.ClaimLedgerOutboxEntity;
import com.ktayl.core.claimsgl.persistence.ClaimLedgerOutboxRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the claims GL outbox to ERPNext on a schedule (ADR-021). Reads {@code pending} postings and hands
 * each to {@link ClaimLedgerPostingService} (one tx per row). A GL outage just leaves rows pending → retried
 * next tick; claims is never blocked. If no ledger client is wired, it no-ops (the outbox accumulates).
 * Reuses billing's drain cadence config ({@code ledger.drain-*}).
 */
@Component
public class ClaimLedgerOutboxDrainer {

    private static final Logger log = LoggerFactory.getLogger(ClaimLedgerOutboxDrainer.class);
    private static final int BATCH = 50;

    private final ClaimLedgerOutboxRepository outbox;
    private final ClaimLedgerPostingService posting;

    public ClaimLedgerOutboxDrainer(ClaimLedgerOutboxRepository outbox, ClaimLedgerPostingService posting) {
        this.outbox = outbox;
        this.posting = posting;
    }

    @Scheduled(fixedDelayString = "${ledger.drain-interval-ms:30000}", initialDelayString = "${ledger.drain-initial-ms:20000}")
    public void drain() {
        if (!posting.ledgerAvailable()) {
            return; // ERPNext not configured — nothing to drain to yet
        }
        List<ClaimLedgerOutboxEntity> batch = outbox.findByStatusOrderByCreatedAt("pending", Limit.of(BATCH));
        if (batch.isEmpty()) {
            return;
        }
        int ok = 0;
        for (ClaimLedgerOutboxEntity row : batch) {
            UUID id = row.getId();
            try {
                posting.post(id);
                ok++;
            } catch (Exception e) {
                log.warn("claims GL post failed for {} ({}), will retry: {}", row.getEventType(), row.getReference(), e.getMessage());
                posting.recordFailure(id, e.getMessage());
            }
        }
        log.debug("claims GL outbox drain: {}/{} posted", ok, batch.size());
    }
}
