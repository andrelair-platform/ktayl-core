package com.ktayl.core.screening.application;

import com.ktayl.core.screening.domain.AlertNotFoundException;
import com.ktayl.core.screening.domain.AlertView;
import com.ktayl.core.screening.domain.CaseDecision;
import com.ktayl.core.screening.domain.CaseStatus;
import com.ktayl.core.screening.domain.Risk;
import com.ktayl.core.screening.domain.ScreeningAuditLog;
import com.ktayl.core.screening.domain.ScreeningStore;
import com.ktayl.core.screening.domain.Vigilance;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case: a human compliance officer decides an alert (RC-02 S1, HITL). Server-side authority — the
 * decision ({@code ACC}/{@code REF}/{@code PENDING} + risk + vigilance + rationale) is persisted as the
 * alert's case and audited ({@code DECIDE}). The module NEVER auto-clears an alert — a HOLD is only resolved
 * by this human decision.
 */
@Service
public class DecideAlert {

    private static final Logger log = LoggerFactory.getLogger(DecideAlert.class);

    private final ScreeningStore store;
    private final ScreeningAuditLog audit;
    private final Clock clock;

    public DecideAlert(ScreeningStore store, ScreeningAuditLog audit, Clock clock) {
        this.store = store;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void decide(UUID alertId, CaseStatus status, Risk risk, Vigilance vigilance, String rationale,
            String actor) {
        AlertView alert = store.findAlert(alertId)
                .orElseThrow(() -> new AlertNotFoundException("no alert " + alertId));

        Instant decidedAt = Instant.now(clock);
        store.saveCase(new CaseDecision(alertId, status, risk, vigilance, actor, rationale, decidedAt));
        audit.record(alert.partyId(), "DECIDE", actor, null,
                "alert=" + alertId + " status=" + status + " risk=" + risk + " vigilance=" + vigilance);
        log.info("alert decided alert={} party={} status={} actor={}", alertId, alert.partyId(), status, actor);
    }
}
