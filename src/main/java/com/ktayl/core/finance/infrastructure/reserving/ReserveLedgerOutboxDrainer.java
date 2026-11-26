package com.ktayl.core.finance.infrastructure.reserving;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the IBNR reserve-movement outbox to ERPNext on a schedule (FIN-03, ADR-022). Reads {@code pending}
 * postings and hands each to {@link ReserveLedgerPostingService} (one tx per row). A GL outage just leaves rows
 * pending → retried next tick; the reserve review is never blocked. If no ledger client is wired it no-ops (the
 * outbox accumulates). Reuses billing's drain cadence config ({@code ledger.drain-*}).
 */
@Component
public class ReserveLedgerOutboxDrainer {

    private static final Logger log = LoggerFactory.getLogger(ReserveLedgerOutboxDrainer.class);
    private static final int BATCH = 50;

    private final ReserveMovementOutboxRepository outbox;
    private final ReserveLedgerPostingService posting;

    public ReserveLedgerOutboxDrainer(ReserveMovementOutboxRepository outbox, ReserveLedgerPostingService posting) {
        this.outbox = outbox;
        this.posting = posting;
    }

    @Scheduled(fixedDelayString = "${ledger.drain-interval-ms:30000}", initialDelayString = "${ledger.drain-initial-ms:20000}")
    public void drain() {
        if (!posting.ledgerAvailable()) {
            return;
        }
        List<ReserveMovementOutboxEntity> batch = outbox.findByStatusOrderByCreatedAt("pending", Limit.of(BATCH));
        if (batch.isEmpty()) {
            return;
        }
        int ok = 0;
        for (ReserveMovementOutboxEntity row : batch) {
            UUID id = row.getId();
            try {
                posting.post(id);
                ok++;
            } catch (Exception e) {
                log.warn("reserve GL post failed for {} ({}), will retry: {}", row.getReference(), row.getPortfolio(), e.getMessage());
                posting.recordFailure(id, e.getMessage());
            }
        }
        log.debug("reserve movement outbox drain: {}/{} posted", ok, batch.size());
    }
}
