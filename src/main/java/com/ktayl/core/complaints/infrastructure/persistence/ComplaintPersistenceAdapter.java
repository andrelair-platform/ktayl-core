package com.ktayl.core.complaints.infrastructure.persistence;

import com.ktayl.core.complaints.domain.Complaint;
import com.ktayl.core.complaints.domain.ComplaintChannel;
import com.ktayl.core.complaints.domain.ComplaintRepository;
import com.ktayl.core.complaints.domain.ComplaintStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * JPA adapter implementing the domain's {@link ComplaintRepository} port (hexagonal, ADR-013). It is the ONLY
 * place that knows the aggregate is stored as a {@link ComplaintJpaEntity} — it maps domain → entity on the
 * way out and entity → domain (via {@link Complaint#rehydrate}) on the way back, so the domain never sees
 * JPA. Swapping the store (another ORM, a document DB) replaces only this class.
 */
@Component
class ComplaintPersistenceAdapter implements ComplaintRepository {

    private final ComplaintJpaRepository jpa;

    ComplaintPersistenceAdapter(ComplaintJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Complaint save(Complaint complaint) {
        return toDomain(jpa.save(toEntity(complaint)));
    }

    @Override
    public Optional<Complaint> findById(UUID id) {
        return jpa.findById(id).map(ComplaintPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Complaint> findByIdempotencyKey(String idempotencyKey) {
        return jpa.findByIdempotencyKey(idempotencyKey).map(ComplaintPersistenceAdapter::toDomain);
    }

    private static ComplaintJpaEntity toEntity(Complaint c) {
        return new ComplaintJpaEntity(c.id(), c.channel().name(), c.subjectRef(), c.summary(),
                c.status().name(), c.registeredAt(), c.idempotencyKey(), c.acknowledgementDueAt(),
                c.assignedTo(), c.resolutionNote());
    }

    private static Complaint toDomain(ComplaintJpaEntity e) {
        return Complaint.rehydrate(e.getId(), ComplaintChannel.valueOf(e.getChannel()), e.getSubjectRef(),
                e.getSummary(), e.getRegisteredAt(), e.getIdempotencyKey(),
                ComplaintStatus.valueOf(e.getStatus()), e.getAcknowledgementDueAt(), e.getAssignedTo(),
                e.getResolutionNote());
    }
}
