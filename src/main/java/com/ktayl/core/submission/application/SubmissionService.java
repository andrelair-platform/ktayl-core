package com.ktayl.core.submission.application;

import com.ktayl.core.screening.api.PartyRef;
import com.ktayl.core.screening.api.ScreeningGate;
import com.ktayl.core.screening.api.ScreeningOutcome;
import com.ktayl.core.submission.api.AddExposureCommand;
import com.ktayl.core.submission.api.RegisterSubmissionCommand;
import com.ktayl.core.submission.api.RouteSubmissionCommand;
import com.ktayl.core.submission.api.SubmissionIntake;
import com.ktayl.core.submission.api.SubmissionQuery;
import com.ktayl.core.submission.api.SubmissionView;
import com.ktayl.core.submission.api.TriageSubmissionCommand;
import com.ktayl.core.submission.domain.ExposureItem;
import com.ktayl.core.submission.domain.InvalidSubmissionException;
import com.ktayl.core.submission.domain.Submission;
import com.ktayl.core.submission.domain.SubmissionNotFoundException;
import com.ktayl.core.submission.domain.SubmissionRepository;
import com.ktayl.core.submission.domain.SubmissionStatus;
import com.ktayl.core.submission.domain.TriageDecision;
import com.ktayl.core.submission.domain.TriageLog;
import com.ktayl.core.submission.domain.UnderwritingHandoffException;
import com.ktayl.core.submission.domain.UnderwritingPort;
import com.ktayl.core.submission.domain.UnderwritingSubmissionMapper;
import com.ktayl.core.submission.domain.UnderwritingSubmissionRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the submission module's published ports {@link SubmissionIntake} + {@link SubmissionQuery}
 * (ADR-005/013) over the pure {@link Submission} aggregate and the outbound ports
 * ({@link SubmissionRepository}, {@link TriageLog}, and the {@code screening} module's published
 * {@link ScreeningGate}) — it holds NO business rules (those live on the aggregate) and NO persistence detail
 * (that lives in the adapter). The only framework here is the Spring wiring (@Service/@Transactional), the
 * allowed application layer per ADR-013 — so the domain stays tech-independent.
 *
 * <p><b>Screening at intake (RC-02):</b> {@link #register} captures the submission in RECEIVED and then
 * screens the insured through {@link ScreeningGate}; a {@code HOLD} verdict (a sanctions match OR a
 * fail-closed engine outage) flags the submission (it is still captured + an auditable alert exists) so it
 * cannot be routed until a compliance officer clears it (HITL). The String {@code status}/{@code decision}
 * from the api contract are parsed here — an unknown value becomes {@link InvalidSubmissionException} (→ 4xx),
 * keeping the domain enum off the boundary.
 */
@Service
public class SubmissionService implements SubmissionIntake, SubmissionQuery {

    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);

    /** The intake gate marker threaded to the screening engine for audit attribution (RC-02). */
    private static final String INTAKE_GATE = "intake";

    private final SubmissionRepository submissions;
    private final TriageLog triageLog;
    private final ScreeningGate screening;
    private final UnderwritingPort underwriting;
    private final Clock clock;

    public SubmissionService(SubmissionRepository submissions, TriageLog triageLog, ScreeningGate screening,
            UnderwritingPort underwriting, Clock clock) {
        this.submissions = submissions;
        this.triageLog = triageLog;
        this.screening = screening;
        this.underwriting = underwriting;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID register(RegisterSubmissionCommand command, String actor) {
        Submission submission = Submission.receive(command.brokerRef(), command.insuredName(),
                command.productLine(), Instant.now(clock));

        // RC-02: screen the insured at intake. A HOLD flags the submission (still captured) — it cannot be
        // routed until a compliance officer clears the hold (HITL; the gate never auto-clears).
        ScreeningOutcome outcome = screening.screen(
                new PartyRef(submission.brokerRef(), submission.insuredName(), null, null, null),
                INTAKE_GATE, actor);
        if ("HOLD".equals(outcome.verdict())) {
            submission.flagScreeningHold(outcome.alertId());
            log.warn("submission {} flagged screening HOLD at intake (alert {})", submission.id(),
                    outcome.alertId());
        }

        Submission saved = submissions.save(submission);
        log.info("submission registered id={} broker={} productLine={} hold={}", saved.id(),
                saved.brokerRef(), saved.productLine(), saved.screeningHold());
        return saved.id();
    }

    @Override
    @Transactional
    public UUID addExposure(UUID submissionId, AddExposureCommand command, String actor) {
        Submission submission = load(submissionId);
        ExposureItem exposure = ExposureItem.create(submission.id(), command.siteName(), command.country(),
                command.tivAmount(), command.currency(), command.occupancy(), command.postcode(),
                command.lossHistory(), command.catFlag(), Instant.now(clock));
        ExposureItem saved = submissions.saveExposure(exposure);
        log.info("submission {} exposure added id={} site={} tiv={} {}", submissionId, saved.id(),
                saved.siteName(), saved.tivAmount(), saved.currency());
        return saved.id();
    }

    @Override
    @Transactional
    public void triage(UUID submissionId, TriageSubmissionCommand command, String actor) {
        Submission submission = load(submissionId);
        TriageDecision decision = parseDecision(command.decision());
        SubmissionStatus from = submission.status();
        submission.triage(decision, Instant.now(clock));   // guarded by the aggregate — throws before any write
        submissions.save(submission);
        triageLog.record(submissionId, command.completenessScore(), command.appetiteFlag(), decision,
                command.reason(), actor);
        log.info("submission {} triaged decision={} by {} ({} -> {})", submissionId, decision, actor, from,
                submission.status());
    }

    /**
     * Route a triaged submission onward AND hand it off to the underwriting (#12) service (SUB-02).
     *
     * <p><b>Order + resilience:</b> the local {@code ROUTED} transition + save happen FIRST (and throw
     * {@link SubmissionScreeningHoldException} before any write if held). Only then is the dossier mapped and
     * POSTed to underwriting via the {@link UnderwritingPort}. If that handoff fails (any HTTP/transport
     * error), an {@link UnderwritingHandoffException} propagates (→ 502) — the submission is NOT left in a
     * half-state: it stays {@code ROUTED} with a null {@code uw_submission_ref}, so the failure is surfaced
     * and the handoff is retryable, never silently swallowed. On success the returned UW submission id is
     * recorded on the aggregate and saved.
     */
    @Override
    @Transactional
    public void route(UUID submissionId, RouteSubmissionCommand command, String actor) {
        Submission submission = load(submissionId);
        SubmissionStatus from = submission.status();
        submission.route(command.target(), Instant.now(clock));  // throws SubmissionScreeningHoldException if held
        submissions.save(submission);
        log.info("submission {} routed to {} by {} ({} -> {})", submissionId, submission.routedTarget(), actor,
                from, submission.status());

        // SUB-02: hand the routed submission off to underwriting (#12) — create a real UW submission from the
        // exposure dossier. DEV: underwriting auth is OFF, the adapter calls unauthenticated (TODO(SUB-02-prod)
        // adds the OAuth2 client-credentials token). A handoff failure → UnderwritingHandoffException (502).
        UnderwritingSubmissionRequest request =
                UnderwritingSubmissionMapper.toRequest(submission, submissions.findExposures(submissionId));
        String uwRef;
        try {
            uwRef = underwriting.createSubmission(request);
        } catch (UnderwritingHandoffException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new UnderwritingHandoffException(submissionId, e.getMessage(), e);
        }
        submission.recordUwHandoff(uwRef);
        submissions.save(submission);
        log.info("submission {} handed off to underwriting as UW submission {}", submissionId, uwRef);
    }

    @Override
    @Transactional
    public void clearScreeningHold(UUID submissionId, String actor) {
        Submission submission = load(submissionId);
        submission.clearScreeningHold(Instant.now(clock));
        submissions.save(submission);
        log.info("submission {} screening hold cleared by {}", submissionId, actor);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SubmissionView> findById(UUID id) {
        return submissions.findById(id).map(SubmissionService::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubmissionView> list(String status, Instant afterReceivedAt, UUID afterId, int limit) {
        SubmissionStatus statusFilter = parseStatus(status);
        return submissions.list(statusFilter, afterReceivedAt, afterId, limit).stream()
                .map(SubmissionService::toView).toList();
    }

    private Submission load(UUID id) {
        return submissions.findById(id).orElseThrow(() -> new SubmissionNotFoundException(id));
    }

    private static TriageDecision parseDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            throw new InvalidSubmissionException("triage decision is required");
        }
        try {
            return TriageDecision.valueOf(decision.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidSubmissionException("unknown triage decision: " + decision);
        }
    }

    private static SubmissionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return SubmissionStatus.valueOf(status.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidSubmissionException("unknown status: " + status);
        }
    }

    private static SubmissionView toView(Submission s) {
        return new SubmissionView(s.id(), s.brokerRef(), s.insuredName(), s.productLine(), s.status().name(),
                s.screeningHold(), s.routedTarget(), s.uwSubmissionRef(), s.receivedAt());
    }
}
