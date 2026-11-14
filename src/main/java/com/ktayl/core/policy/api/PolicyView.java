package com.ktayl.core.policy.api;

import java.time.LocalDate;
import java.util.UUID;

/** The policy read model other modules (or the API) see — never the internal aggregate/JPA entity. */
public record PolicyView(UUID id, String policyNumber, String holderName, String productCode,
        String status, LocalDate effectiveDate, LocalDate expiryDate) {}
