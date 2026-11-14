package com.ktayl.core.policy.infrastructure.persistence;

import com.ktayl.core.policy.api.PolicyQuery;
import com.ktayl.core.policy.api.PolicyView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements the published {@link PolicyQuery} read interface — maps the JPA entity to the {@link PolicyView}
 * read model for in-process reads by other modules. Kept separate from {@code PolicyPersistenceAdapter}
 * because the domain-port and the read-API methods share signatures but return different types.
 */
@Component
class PolicyQueryAdapter implements PolicyQuery {

    private final PolicyJpaRepository jpa;

    PolicyQueryAdapter(PolicyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<PolicyView> findByPolicyNumber(String policyNumber) {
        return jpa.findByPolicyNumber(policyNumber).map(PolicyQueryAdapter::toView);
    }

    @Override
    public Optional<PolicyView> findById(UUID id) {
        return jpa.findById(id).map(PolicyQueryAdapter::toView);
    }

    private static PolicyView toView(PolicyJpaEntity e) {
        return new PolicyView(e.getId(), e.getPolicyNumber(), e.getHolderName(), e.getProductCode(),
                e.getStatus(), e.getEffectiveDate(), e.getExpiryDate());
    }
}
