package com.ktayl.core.screening.application;

import com.ktayl.core.screening.domain.Party;
import com.ktayl.core.screening.domain.ScreeningAuditLog;
import com.ktayl.core.screening.domain.ScreeningEngine;
import com.ktayl.core.screening.domain.ScreeningEngineUnavailableException;
import com.ktayl.core.screening.domain.ScreeningRecord;
import com.ktayl.core.screening.domain.ScreeningResult;
import com.ktayl.core.screening.domain.ScreeningStore;
import com.ktayl.core.screening.domain.Verdict;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case: screen a party at a gate (RC-02 S1). Orchestrates the pure domain + the outbound ports
 * ({@link ScreeningEngine}, {@link ScreeningStore}, {@link ScreeningAuditLog}) — it holds the fail-closed +
 * idempotency orchestration but NO match rule (the ALLOW/HOLD rule is {@link ScreeningResult#verdict()}).
 *
 * <p><b>Fail-closed (LCB-FT):</b> if the engine throws {@link ScreeningEngineUnavailableException} the party
 * is HELD (never silent-ALLOWed) — a HOLD screening is persisted with a null dataset_version and the action
 * audited as {@code SCREEN_FAILCLOSED}.
 *
 * <p><b>Idempotent per day:</b> a repeat for the same party ({@code externalRef}) + gate on the same calendar
 * day returns the prior screening rather than re-screening (no second engine call / audit).
 */
@Service
public class ScreenParty {

    private static final Logger log = LoggerFactory.getLogger(ScreenParty.class);

    private final ScreeningEngine engine;
    private final ScreeningStore store;
    private final ScreeningAuditLog audit;
    private final Clock clock;

    public ScreenParty(ScreeningEngine engine, ScreeningStore store, ScreeningAuditLog audit, Clock clock) {
        this.engine = engine;
        this.store = store;
        this.audit = audit;
        this.clock = clock;
    }

    /** The application-layer result of {@link #screen}. */
    public record Outcome(Verdict verdict, UUID alertId, String datasetVersion) {}

    @Transactional
    public Outcome screen(Party party, String gate, String actor) {
        Party persisted = resolveParty(party);

        LocalDate day = LocalDate.ofInstant(Instant.now(clock), ZoneOffset.UTC);
        Optional<ScreeningRecord> prior = store.findScreening(persisted.id(), gate, day);
        if (prior.isPresent()) {
            ScreeningRecord r = prior.get();
            log.info("screening idempotent-hit party={} gate={} day={} verdict={}", persisted.id(), gate, day,
                    r.verdict());
            return new Outcome(r.verdict(), firstAlertId(r), r.datasetVersion());
        }

        ScreeningResult result;
        try {
            result = engine.match(persisted);
        } catch (ScreeningEngineUnavailableException e) {
            // FAIL-CLOSED: engine down → HOLD, never silent-ALLOW. Persist a HOLD with no dataset version.
            ScreeningRecord held = store.saveScreening(persisted.id(), gate, Instant.now(clock), null,
                    Verdict.HOLD, List.of());
            audit.record(persisted.id(), "SCREEN_FAILCLOSED", actor, null,
                    "gate=" + gate + " engine unavailable: " + e.getMessage());
            log.warn("screening fail-closed HOLD party={} gate={}: {}", persisted.id(), gate, e.getMessage());
            return new Outcome(Verdict.HOLD, null, null);
        }

        Verdict verdict = result.verdict();
        ScreeningRecord saved = store.saveScreening(persisted.id(), gate, Instant.now(clock),
                result.datasetVersion(), verdict, result.hits());
        audit.record(persisted.id(), "SCREEN", actor, result.datasetVersion(),
                "gate=" + gate + " verdict=" + verdict + " hits=" + result.hits().size());
        log.info("screening party={} gate={} verdict={} hits={} dataset={}", persisted.id(), gate, verdict,
                result.hits().size(), result.datasetVersion());
        return new Outcome(verdict, firstAlertId(saved), result.datasetVersion());
    }

    /** Reuse an existing party by external ref (idempotency) else persist the new one. */
    private Party resolveParty(Party party) {
        if (party.externalRef() != null && !party.externalRef().isBlank()) {
            Optional<Party> existing = store.findPartyByExternalRef(party.externalRef());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        return store.saveParty(party);
    }

    private static UUID firstAlertId(ScreeningRecord r) {
        List<UUID> ids = r.alertIds();
        return ids.isEmpty() ? null : ids.get(0);
    }
}
