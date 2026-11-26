package com.ktayl.core.finance.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The published port for actuarial reserving (FIN-03) — the finance module's IBNR capability: load a loss
 * triangle, estimate IBNR (Chain-Ladder / Bornhuetter-Ferguson), and run an IBNR reserve through a
 * maker-checker review that books the approved movement to the GL. Part of the finance {@code api} named
 * interface; other modules depend only on this.
 */
public interface ReservingOperations {

    /** Load (replace) the loss triangle for {@code (portfolio, asOf, basis)}. */
    void loadTriangle(TriangleLoadRequest request, String actor);

    /**
     * Dry-run an IBNR estimate over the stored triangle — no persistence (lets a user compare methods before
     * submitting). A malformed triangle → {@code TriangleShapeException}; no triangle → {@code TriangleNotFound}.
     */
    IbnrEstimateView estimate(String portfolio, LocalDate asOf, String basis, String method,
            Map<String, Long> aprioriUltimateMinor);

    /** Submit an IBNR estimate as a PENDING reserve review for an independent checker (the maker is the caller). */
    ReserveReviewView submitReview(ReserveReviewRequest request, String maker);

    /** Approve a PENDING review (checker ≠ maker) → books the signed IBNR movement to the GL. */
    ReserveReviewView approve(UUID reviewId, String note, String checker);

    /** Reject a PENDING review (checker ≠ maker) with a reason → nothing booked. */
    ReserveReviewView reject(UUID reviewId, String reason, String checker);

    ReserveReviewView getReview(UUID reviewId);

    /** Reviews for a portfolio newest-first, or all reviews when {@code portfolio} is null/blank. */
    List<ReserveReviewView> listReviews(String portfolio);
}
