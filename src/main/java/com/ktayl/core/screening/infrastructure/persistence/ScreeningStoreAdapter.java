package com.ktayl.core.screening.infrastructure.persistence;

import com.ktayl.core.screening.domain.AlertView;
import com.ktayl.core.screening.domain.Bucket;
import com.ktayl.core.screening.domain.CaseDecision;
import com.ktayl.core.screening.domain.CaseStatus;
import com.ktayl.core.screening.domain.Hit;
import com.ktayl.core.screening.domain.Party;
import com.ktayl.core.screening.domain.ScreeningRecord;
import com.ktayl.core.screening.domain.ScreeningStore;
import com.ktayl.core.screening.domain.Verdict;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * JPA adapter implementing the domain's {@link ScreeningStore} port (hexagonal, ADR-013). The ONLY place that
 * knows the screening aggregate is stored as {@link PartyEntity}/{@link ScreeningEntity}/{@link AlertEntity}/
 * {@link CaseEntity} — it maps domain ↔ entity so the domain never sees JPA. Enums are stored as their String
 * name (varchar), matched by the V11 CHECK constraints.
 */
@Component
class ScreeningStoreAdapter implements ScreeningStore {

    private final PartyJpaRepository parties;
    private final ScreeningJpaRepository screenings;
    private final AlertJpaRepository alerts;
    private final CaseJpaRepository cases;
    private final Clock clock;

    ScreeningStoreAdapter(PartyJpaRepository parties, ScreeningJpaRepository screenings,
            AlertJpaRepository alerts, CaseJpaRepository cases, Clock clock) {
        this.parties = parties;
        this.screenings = screenings;
        this.alerts = alerts;
        this.cases = cases;
        this.clock = clock;
    }

    @Override
    public Party saveParty(Party party) {
        UUID id = party.id() != null ? party.id() : UUID.randomUUID();
        PartyEntity saved = parties.save(new PartyEntity(id, party.externalRef(), party.name(), party.dob(),
                party.nationality(), party.residence(), party.ids(), party.uboOf(), Instant.now(clock)));
        return toDomain(saved);
    }

    @Override
    public Optional<Party> findPartyByExternalRef(String externalRef) {
        return parties.findByExternalRef(externalRef).map(ScreeningStoreAdapter::toDomain);
    }

    @Override
    public Optional<Party> findParty(UUID partyId) {
        return parties.findById(partyId).map(ScreeningStoreAdapter::toDomain);
    }

    @Override
    public Optional<ScreeningRecord> findScreening(UUID partyId, String gate, LocalDate day) {
        Instant start = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<ScreeningEntity> rows = screenings.findForPartyGateDay(partyId, gate, start, end,
                PageRequest.of(0, 1));
        return rows.isEmpty() ? Optional.empty() : Optional.of(toRecord(rows.get(0)));
    }

    /** The alert ids of a screening, ordered — so an idempotent HOLD return still carries its first alert. */
    private List<UUID> alertIdsOf(UUID screeningId) {
        return alerts.findByScreeningIdOrderById(screeningId).stream().map(AlertEntity::getId).toList();
    }

    @Override
    public ScreeningRecord saveScreening(UUID partyId, String gate, Instant at, String datasetVersion,
            Verdict verdict, List<Hit> hits) {
        UUID screeningId = UUID.randomUUID();
        screenings.save(new ScreeningEntity(screeningId, partyId, gate, at, datasetVersion, verdict.name()));
        List<UUID> alertIds = new ArrayList<>();
        List<Hit> savedHits = hits == null ? List.of() : hits;
        for (Hit h : savedHits) {
            UUID alertId = UUID.randomUUID();
            alerts.save(new AlertEntity(alertId, screeningId, h.bucket().name(), h.score(), h.matchedEntry()));
            alertIds.add(alertId);
        }
        return new ScreeningRecord(screeningId, partyId, gate, at, datasetVersion, verdict, alertIds, savedHits);
    }

    @Override
    public Optional<Verdict> findLatestStatus(UUID partyId) {
        return screenings.findFirstByPartyIdOrderByAtDesc(partyId)
                .map(e -> Verdict.valueOf(e.getVerdict()));
    }

    @Override
    public List<AlertView> listAlerts(CaseStatus status, Bucket bucket, UUID afterId, int limit) {
        // Over-fetch when a post-fetch status filter is applied, so the page still fills after filtering.
        int fetch = status == null ? limit : Math.min(limit * 4, 400);
        List<AlertRow> rows = alerts.listAlerts(bucket == null ? null : bucket.name(), afterId,
                PageRequest.of(0, fetch));
        List<AlertView> views = new ArrayList<>();
        for (AlertRow r : rows) {
            CaseStatus caseStatus = r.caseStatus() == null ? null : CaseStatus.valueOf(r.caseStatus());
            if (status != null && caseStatus != status) {
                continue;
            }
            views.add(toView(r, caseStatus));
            if (views.size() == limit) {
                break;
            }
        }
        return views;
    }

    @Override
    public Optional<AlertView> findAlert(UUID alertId) {
        List<AlertRow> rows = alerts.findAlertRow(alertId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        AlertRow r = rows.get(0);
        CaseStatus caseStatus = r.caseStatus() == null ? null : CaseStatus.valueOf(r.caseStatus());
        return Optional.of(toView(r, caseStatus));
    }

    @Override
    public void saveCase(CaseDecision decision) {
        CaseEntity entity = cases.findByAlertId(decision.alertId())
                .map(existing -> new CaseEntity(existing.getId(), decision.alertId(),
                        decision.status().name(), name(decision.risk()), name(decision.vigilance()),
                        decision.actorId(), decision.rationale(), decision.decidedAt()))
                .orElseGet(() -> new CaseEntity(UUID.randomUUID(), decision.alertId(),
                        decision.status().name(), name(decision.risk()), name(decision.vigilance()),
                        decision.actorId(), decision.rationale(), decision.decidedAt()));
        cases.save(entity);
    }

    private static String name(Enum<?> e) {
        return e == null ? null : e.name();
    }

    private static Party toDomain(PartyEntity e) {
        return new Party(e.getId(), e.getExternalRef(), e.getName(), e.getDob(), e.getNationality(),
                e.getResidence(), e.getIds(), e.getUboOf());
    }

    private ScreeningRecord toRecord(ScreeningEntity e) {
        return new ScreeningRecord(e.getId(), e.getPartyId(), e.getGate(), e.getAt(), e.getDatasetVersion(),
                Verdict.valueOf(e.getVerdict()), alertIdsOf(e.getId()), List.of());
    }

    private static AlertView toView(AlertRow r, CaseStatus caseStatus) {
        return new AlertView(r.alertId(), r.screeningId(), r.partyId(), Bucket.valueOf(r.bucket()),
                r.score(), r.matchedEntry(), caseStatus);
    }
}
