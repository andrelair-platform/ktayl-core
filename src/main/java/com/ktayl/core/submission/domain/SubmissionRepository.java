package com.ktayl.core.submission.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * OUTBOUND PORT (hexagonal, ADR-013) — the domain's view of persistence. An interface, owned by the domain
 * and expressed in domain terms ({@link Submission} / {@link ExposureItem}); the JPA adapter in
 * {@code infrastructure.persistence} implements it. The application use case depends on THIS, never on
 * Spring Data / JPA, so the storage tech is swappable without touching the business logic.
 */
public interface SubmissionRepository {

    /** Persist (insert or update) a submission aggregate and return it. */
    Submission save(Submission submission);

    Optional<Submission> findById(UUID id);

    /** Persist an exposure item (append to a submission's dossier) and return it. */
    ExposureItem saveExposure(ExposureItem exposure);

    /** The exposure dossier for a submission, in insertion order. */
    List<ExposureItem> findExposures(UUID submissionId);

    /**
     * A keyset page of submissions ordered by {@code received_at, id} descending-stable, optionally filtered
     * by {@code status}. {@code afterReceivedAt}/{@code afterId} are the cursor (null = first page);
     * {@code limit} caps the page size. Mirrors the policy module's list keyset.
     */
    List<Submission> list(SubmissionStatus status, java.time.Instant afterReceivedAt, UUID afterId, int limit);
}
