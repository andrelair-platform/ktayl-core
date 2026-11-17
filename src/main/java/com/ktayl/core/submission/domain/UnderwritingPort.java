package com.ktayl.core.submission.domain;

/**
 * OUTBOUND PORT (hexagonal, ADR-013) — the domain's view of the underwriting (#12) service. An interface,
 * owned by the domain and expressed in domain terms ({@link UnderwritingSubmissionRequest}); the HTTP adapter
 * in {@code infrastructure.underwriting} implements it. The application use case depends on THIS, never on
 * {@code RestClient} / HTTP, so the handoff transport is swappable without touching the business logic.
 *
 * <p>SUB-02: {@code route()} hands a ROUTED submission off here — the adapter creates a real underwriting
 * submission from the exposure dossier and returns its id. Any transport/HTTP failure surfaces as
 * {@link UnderwritingHandoffException} (never swallowed): the submission is already ROUTED locally, but the
 * handoff failed and is retryable.
 */
public interface UnderwritingPort {

    /**
     * Create an underwriting submission from the mapped dossier and return its id.
     *
     * @throws UnderwritingHandoffException if the underwriting service cannot be reached or rejects the request
     */
    String createSubmission(UnderwritingSubmissionRequest request);
}
