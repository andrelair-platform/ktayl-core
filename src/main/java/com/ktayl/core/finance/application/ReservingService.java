package com.ktayl.core.finance.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktayl.core.finance.api.IbnrEstimateView;
import com.ktayl.core.finance.api.ReserveReviewRequest;
import com.ktayl.core.finance.api.ReserveReviewView;
import com.ktayl.core.finance.api.ReservingOperations;
import com.ktayl.core.finance.api.TriangleLoadRequest;
import com.ktayl.core.finance.domain.reserving.BornhuetterFerguson;
import com.ktayl.core.finance.domain.reserving.ChainLadder;
import com.ktayl.core.finance.domain.reserving.IbnrEstimate;
import com.ktayl.core.finance.domain.reserving.InvalidReserveRequestException;
import com.ktayl.core.finance.domain.reserving.LossTriangle;
import com.ktayl.core.finance.domain.reserving.ReserveReviewNotFoundException;
import com.ktayl.core.finance.domain.reserving.ReserveReviewState;
import com.ktayl.core.finance.domain.reserving.ReservingMethod;
import com.ktayl.core.finance.domain.reserving.TriangleNotFoundException;
import com.ktayl.core.finance.infrastructure.persistence.LossTriangleCellEntity;
import com.ktayl.core.finance.infrastructure.persistence.LossTriangleCellRepository;
import com.ktayl.core.finance.infrastructure.persistence.ReserveReviewEntity;
import com.ktayl.core.finance.infrastructure.persistence.ReserveReviewRepository;
import com.ktayl.core.finance.infrastructure.reserving.ReserveMovementOutboxEntity;
import com.ktayl.core.finance.infrastructure.reserving.ReserveMovementOutboxRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The actuarial reserving use cases (FIN-03) — implements {@link ReservingOperations}. Wires the stored loss
 * triangle + the pure calculators ({@link ChainLadder}, {@link BornhuetterFerguson}) + the maker-checker
 * {@link ReserveReviewEntity} + the GL outbox. The estimation math is pure domain; this layer handles
 * persistence, the four-eyes orchestration, and the transactional-outbox write on approval.
 */
@Service
public class ReservingService implements ReservingOperations {

    private static final Logger log = LoggerFactory.getLogger(ReservingService.class);

    private final LossTriangleCellRepository triangles;
    private final ReserveReviewRepository reviews;
    private final ReserveMovementOutboxRepository outbox;
    private final ObjectMapper json;

    public ReservingService(LossTriangleCellRepository triangles, ReserveReviewRepository reviews,
            ReserveMovementOutboxRepository outbox, ObjectMapper json) {
        this.triangles = triangles;
        this.reviews = reviews;
        this.outbox = outbox;
        this.json = json;
    }

    @Override
    @Transactional
    public void loadTriangle(TriangleLoadRequest request, String actor) {
        String basis = basis(request.basis());
        if (request.cells() == null || request.cells().isEmpty()) {
            throw new InvalidReserveRequestException("a triangle load must carry at least one cell");
        }
        // replace-on-load: clear the prior triangle for this key, then insert
        triangles.deleteByPortfolioAndAsOfAndBasis(request.portfolio(), request.asOf(), basis);
        Instant now = Instant.now();
        for (TriangleLoadRequest.Cell c : request.cells()) {
            triangles.save(new LossTriangleCellEntity(UUID.randomUUID(), request.portfolio(), request.asOf(),
                    basis, c.accidentPeriod(), c.developmentLag(), c.cumulativeMinor(), request.currency(), now, actor));
        }
        log.info("loaded loss triangle portfolio={} as-of={} basis={} ({} cells) by {}",
                request.portfolio(), request.asOf(), basis, request.cells().size(), actor);
    }

    @Override
    @Transactional(readOnly = true)
    public IbnrEstimateView estimate(String portfolio, LocalDate asOf, String basis, String method,
            Map<String, Long> aprioriUltimateMinor) {
        String b = basis(basis);
        ReservingMethod m = method(method);
        LossTriangle triangle = loadTriangle(portfolio, asOf, b);
        return toView(run(triangle, m, aprioriUltimateMinor), currencyOf(portfolio, asOf, b));
    }

    @Override
    @Transactional
    public ReserveReviewView submitReview(ReserveReviewRequest request, String maker) {
        String basis = basis(request.basis());
        ReservingMethod method = method(request.method());
        // one open PENDING review per (portfolio, as-of, basis, method) — a re-submit returns the existing one
        var existing = reviews.findFirstByPortfolioAndAsOfAndBasisAndMethodAndState(
                request.portfolio(), request.asOf(), basis, method.name(), ReserveReviewState.PENDING);
        if (existing.isPresent()) {
            return toView(existing.get());
        }
        LossTriangle triangle = loadTriangle(request.portfolio(), request.asOf(), basis);
        IbnrEstimate estimate = run(triangle, method, request.aprioriUltimateMinor());
        String currency = currencyOf(request.portfolio(), request.asOf(), basis);
        ReserveReviewEntity review = new ReserveReviewEntity(UUID.randomUUID(), request.portfolio(),
                request.asOf(), basis, method.name(), currency, estimate.totalLatestMinor(),
                estimate.totalUltimateMinor(), estimate.totalIbnrMinor(),
                writeDetail(toView(estimate, currency)), maker, Instant.now());
        reviews.save(review);
        log.info("reserve review {} submitted by {}: portfolio={} method={} IBNR={} {}", review.getId(), maker,
                request.portfolio(), method, estimate.totalIbnrMinor(), currency);
        return toView(review);
    }

    @Override
    @Transactional
    public ReserveReviewView approve(UUID reviewId, String note, String checker) {
        ReserveReviewEntity review = reviews.findById(reviewId)
                .orElseThrow(() -> new ReserveReviewNotFoundException(reviewId));
        // movement to book = this review's IBNR − the last approved position for the portfolio (threat T7: the
        // entity itself refuses a self-approval / a non-PENDING review)
        long lastPosition = reviews.findFirstByPortfolioAndStateOrderByDecidedAtDesc(
                        review.getPortfolio(), ReserveReviewState.APPROVED)
                .map(ReserveReviewEntity::getEstimatedIbnrMinor).orElse(0L);
        long movement = review.getEstimatedIbnrMinor() - lastPosition;
        review.approve(checker, movement, note, Instant.now());
        // transactional outbox: enqueue the GL movement in the SAME tx (only when non-zero — a zero movement
        // needs no JE). Idempotent by review id.
        if (movement != 0 && !outbox.existsByReference(reviewId.toString())) {
            outbox.save(new ReserveMovementOutboxEntity(UUID.randomUUID(), reviewId, review.getPortfolio(),
                    movement, review.getCurrency(), reviewId.toString(), Instant.now()));
        }
        log.info("reserve review {} APPROVED by {} (maker {}); IBNR position {} → movement {} {} queued to GL",
                reviewId, checker, review.getMaker(), review.getEstimatedIbnrMinor(), movement, review.getCurrency());
        return toView(review);
    }

    @Override
    @Transactional
    public ReserveReviewView reject(UUID reviewId, String reason, String checker) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidReserveRequestException("a reserve review rejection requires a reason");
        }
        ReserveReviewEntity review = reviews.findById(reviewId)
                .orElseThrow(() -> new ReserveReviewNotFoundException(reviewId));
        review.reject(checker, reason, Instant.now());
        log.info("reserve review {} REJECTED by {} (maker {}): {}", reviewId, checker, review.getMaker(), reason);
        return toView(review);
    }

    @Override
    @Transactional(readOnly = true)
    public ReserveReviewView getReview(UUID reviewId) {
        return toView(reviews.findById(reviewId)
                .orElseThrow(() -> new ReserveReviewNotFoundException(reviewId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReserveReviewView> listReviews(String portfolio) {
        List<ReserveReviewEntity> rows = portfolio == null || portfolio.isBlank()
                ? reviews.findAllByOrderByCreatedAtDesc()
                : reviews.findByPortfolioOrderByCreatedAtDesc(portfolio);
        return rows.stream().map(this::toView).toList();
    }

    // --- internals --------------------------------------------------------------------------------

    private LossTriangle loadTriangle(String portfolio, LocalDate asOf, String basis) {
        List<LossTriangleCellEntity> cells = triangles.findByPortfolioAndAsOfAndBasis(portfolio, asOf, basis);
        if (cells.isEmpty()) {
            throw new TriangleNotFoundException(portfolio, String.valueOf(asOf), basis);
        }
        Map<String, Map<Integer, Long>> grouped = new TreeMap<>();
        for (LossTriangleCellEntity c : cells) {
            grouped.computeIfAbsent(c.getAccidentPeriod(), k -> new TreeMap<>())
                    .put(c.getDevelopmentLag(), c.getCumulativeMinor());
        }
        return LossTriangle.of(grouped); // throws TriangleShapeException on a malformed staircase
    }

    private static IbnrEstimate run(LossTriangle triangle, ReservingMethod method, Map<String, Long> apriori) {
        return switch (method) {
            case CHAIN_LADDER -> ChainLadder.estimate(triangle);
            case BORNHUETTER_FERGUSON -> {
                if (apriori == null || apriori.isEmpty()) {
                    throw new InvalidReserveRequestException(
                            "Bornhuetter-Ferguson requires an a-priori ultimate per accident period");
                }
                yield BornhuetterFerguson.estimate(triangle, apriori);
            }
        };
    }

    private String currencyOf(String portfolio, LocalDate asOf, String basis) {
        return triangles.findByPortfolioAndAsOfAndBasis(portfolio, asOf, basis).stream()
                .map(LossTriangleCellEntity::getCurrency).findFirst().orElse("EUR");
    }

    private static String basis(String raw) {
        if (raw == null) {
            throw new InvalidReserveRequestException("basis is required (PAID or INCURRED)");
        }
        String b = raw.trim().toUpperCase();
        if (!b.equals("PAID") && !b.equals("INCURRED")) {
            throw new InvalidReserveRequestException("basis must be PAID or INCURRED, was: " + raw);
        }
        return b;
    }

    private static ReservingMethod method(String raw) {
        try {
            return ReservingMethod.valueOf(raw == null ? "" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidReserveRequestException(
                    "method must be CHAIN_LADDER or BORNHUETTER_FERGUSON, was: " + raw);
        }
    }

    private static IbnrEstimateView toView(IbnrEstimate e, String currency) {
        List<IbnrEstimateView.PeriodView> periods = e.accidentPeriods().stream()
                .map(p -> new IbnrEstimateView.PeriodView(p.accidentPeriod(), p.latestMinor(),
                        p.developmentFactorToUltimate(), p.ultimateMinor(), p.ibnrMinor()))
                .toList();
        return new IbnrEstimateView(e.method().name(), currency, e.totalLatestMinor(), e.totalUltimateMinor(),
                e.totalIbnrMinor(), periods);
    }

    private ReserveReviewView toView(ReserveReviewEntity r) {
        return new ReserveReviewView(r.getId(), r.getPortfolio(), r.getAsOf(), r.getBasis(), r.getMethod(),
                r.getCurrency(), r.getLatestMinor(), r.getEstimatedUltimateMinor(), r.getEstimatedIbnrMinor(),
                r.getState().name(), r.getMaker(), r.getChecker(), r.getBookedMovementMinor(), r.getReason(),
                r.getCreatedAt(), r.getDecidedAt(), readDetail(r.getDetailJson()));
    }

    private String writeDetail(IbnrEstimateView view) {
        try {
            return json.writeValueAsString(view);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("could not serialise the IBNR estimate detail", ex);
        }
    }

    private IbnrEstimateView readDetail(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return null;
        }
        try {
            return json.readValue(detailJson, IbnrEstimateView.class);
        } catch (JsonProcessingException ex) {
            log.warn("could not read stored IBNR estimate detail: {}", ex.getMessage());
            return null; // non-fatal: the booked totals on the review are the source of truth
        }
    }
}
