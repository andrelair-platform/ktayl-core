package com.ktayl.core.policy.infrastructure.persistence;

import com.ktayl.core.policy.domain.Policy;
import com.ktayl.core.policy.domain.PolicyRepository;
import com.ktayl.core.policy.domain.PolicyStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * The persistence adapter (hexagonal, ADR-013): implements the domain {@link PolicyRepository} outbound
 * port, mapping between the aggregate and the JPA entity. The domain never sees JPA.
 */
@Component
class PolicyPersistenceAdapter implements PolicyRepository {

    private final PolicyJpaRepository jpa;

    PolicyPersistenceAdapter(PolicyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Policy save(Policy policy) {
        jpa.save(toEntity(policy));
        return policy;
    }

    @Override
    public Optional<Policy> findById(UUID id) {
        return jpa.findById(id).map(PolicyPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Policy> findByPolicyNumber(String policyNumber) {
        return jpa.findByPolicyNumber(policyNumber).map(PolicyPersistenceAdapter::toDomain);
    }

    @Override
    public List<Policy> list(PolicyStatus status, Instant afterCreatedAt, UUID afterId, int limit) {
        String s = status == null ? null : status.name();
        PageRequest page = PageRequest.of(0, limit);
        List<PolicyJpaEntity> rows = (afterCreatedAt == null)
                ? jpa.listFirstPage(s, page)
                : jpa.listAfter(s, afterCreatedAt, afterId, page);
        return rows.stream().map(PolicyPersistenceAdapter::toDomain).toList();
    }

    static PolicyJpaEntity toEntity(Policy p) {
        return new PolicyJpaEntity(p.id(), p.policyNumber(), p.holderName(), p.productCode(), p.status().name(),
                p.effectiveDate(), p.expiryDate(), p.createdAt(), p.updatedAt());
    }

    static Policy toDomain(PolicyJpaEntity e) {
        return Policy.rehydrate(e.getId(), e.getPolicyNumber(), e.getHolderName(), e.getProductCode(),
                e.getEffectiveDate(), e.getExpiryDate(), PolicyStatus.valueOf(e.getStatus()),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
