package com.ktayl.core.billing.ingest;

import com.ktayl.core.billing.persistence.AuditEntity;
import com.ktayl.core.billing.persistence.AuditRepository;
import com.ktayl.core.billing.persistence.IngestedPolicyEntity;
import com.ktayl.core.billing.persistence.IngestedPolicyRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingests a bound policy's premium from the UW bound-risk event (BILL-011, ADR-003). Idempotent by
 * {@code policy_number} (a replayed/redelivered event is a no-op) and auditable. Invalid events are
 * rejected with {@link IllegalArgumentException} so the consumer can TERM them (not endlessly redeliver).
 */
@Service
public class PolicyIngestService {

    private static final Logger log = LoggerFactory.getLogger(PolicyIngestService.class);
    private static final String ACTOR = "system:uw-ingest";

    public enum Outcome { INGESTED, SKIPPED_DUPLICATE }

    private final IngestedPolicyRepository policies;
    private final AuditRepository audit;

    public PolicyIngestService(IngestedPolicyRepository policies, AuditRepository audit) {
        this.policies = policies;
        this.audit = audit;
    }

    @Transactional
    public Outcome ingest(BoundRiskEvent e) {
        validate(e);
        if (policies.existsById(e.policyNumber())) {
            log.debug("bound-risk {} already ingested — skip (idempotent)", e.policyNumber());
            return Outcome.SKIPPED_DUPLICATE;
        }
        policies.save(new IngestedPolicyEntity(
                e.policyNumber(), e.premiumMinor(), e.currency(), e.productCode(),
                e.effectiveDate(), e.expiryDate(), e.submissionId(), e.quoteId(),
                "ingested", Instant.now()));
        audit.save(new AuditEntity("ingested_policy", e.policyNumber(), "INGEST", ACTOR,
                "premium_minor=" + e.premiumMinor() + " " + e.currency(), Instant.now()));
        log.info("ingested bound policy {} ({} {} eurocents)", e.policyNumber(), e.premiumMinor(), e.currency());
        return Outcome.INGESTED;
    }

    private static void validate(BoundRiskEvent e) {
        if (e == null) throw new IllegalArgumentException("null event");
        if (isBlank(e.policyNumber())) throw new IllegalArgumentException("policy_number is required");
        if (e.premiumMinor() <= 0) throw new IllegalArgumentException("premium_minor must be > 0");
        if (e.currency() == null || e.currency().length() != 3) throw new IllegalArgumentException("currency must be ISO-4217");
        if (isBlank(e.productCode())) throw new IllegalArgumentException("product_code is required");
        if (e.effectiveDate() == null || e.expiryDate() == null) throw new IllegalArgumentException("effective/expiry dates are required");
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
