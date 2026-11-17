package com.ktayl.core.submission.infrastructure.persistence;

import com.ktayl.core.submission.domain.ExposureItem;
import com.ktayl.core.submission.domain.Submission;
import com.ktayl.core.submission.domain.SubmissionRepository;
import com.ktayl.core.submission.domain.SubmissionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * JPA adapter implementing the domain's {@link SubmissionRepository} port (hexagonal, ADR-013). It is the
 * ONLY place that knows the aggregate is stored as a {@link SubmissionJpaEntity} + {@link ExposureItemJpaEntity}
 * — it maps domain → entity on the way out and entity → domain (via {@link Submission#rehydrate} /
 * {@link ExposureItem#rehydrate}) on the way back, so the domain never sees JPA. Swapping the store replaces
 * only this class.
 */
@Component
class SubmissionPersistenceAdapter implements SubmissionRepository {

    private final SubmissionJpaRepository submissions;
    private final ExposureItemJpaRepository exposures;

    SubmissionPersistenceAdapter(SubmissionJpaRepository submissions, ExposureItemJpaRepository exposures) {
        this.submissions = submissions;
        this.exposures = exposures;
    }

    @Override
    public Submission save(Submission submission) {
        return toDomain(submissions.save(toEntity(submission)));
    }

    @Override
    public Optional<Submission> findById(UUID id) {
        return submissions.findById(id).map(SubmissionPersistenceAdapter::toDomain);
    }

    @Override
    public ExposureItem saveExposure(ExposureItem exposure) {
        return toDomain(exposures.save(toEntity(exposure)));
    }

    @Override
    public List<ExposureItem> findExposures(UUID submissionId) {
        return exposures.findBySubmissionIdOrderByCreatedAtAsc(submissionId).stream()
                .map(SubmissionPersistenceAdapter::toDomain).toList();
    }

    @Override
    public List<Submission> list(SubmissionStatus status, Instant afterReceivedAt, UUID afterId, int limit) {
        String s = status == null ? null : status.name();
        PageRequest page = PageRequest.of(0, limit);
        List<SubmissionJpaEntity> rows = (afterReceivedAt == null || afterId == null)
                ? submissions.listFirstPage(s, page)
                : submissions.listAfter(s, afterReceivedAt, afterId, page);
        return rows.stream().map(SubmissionPersistenceAdapter::toDomain).toList();
    }

    private static SubmissionJpaEntity toEntity(Submission s) {
        return new SubmissionJpaEntity(s.id(), s.brokerRef(), s.insuredName(), s.productLine(),
                s.status().name(), s.screeningHold(), s.screeningAlertId(), s.routedTarget(),
                s.uwSubmissionRef(), s.receivedAt(), s.updatedAt());
    }

    private static Submission toDomain(SubmissionJpaEntity e) {
        return Submission.rehydrate(e.getId(), e.getBrokerRef(), e.getInsuredName(), e.getProductLine(),
                e.getReceivedAt(), SubmissionStatus.valueOf(e.getStatus()), e.isScreeningHold(),
                e.getScreeningAlertId(), e.getRoutedTarget(), e.getUwSubmissionRef(), e.getUpdatedAt());
    }

    private static ExposureItemJpaEntity toEntity(ExposureItem x) {
        return new ExposureItemJpaEntity(x.id(), x.submissionId(), x.siteName(), x.country(), x.tivAmount(),
                x.currency(), x.occupancy(), x.postcode(), x.lossHistory(), x.catFlag(), x.createdAt());
    }

    private static ExposureItem toDomain(ExposureItemJpaEntity e) {
        return ExposureItem.rehydrate(e.getId(), e.getSubmissionId(), e.getSiteName(), e.getCountry(),
                e.getTivAmount(), e.getCurrency(), e.getOccupancy(), e.getPostcode(), e.getLossHistory(),
                e.isCatFlag(), e.getCreatedAt());
    }
}
