package com.ktayl.core.policy.application;

import java.time.LocalDate;

/** The mutable policy details an update may change (BF-POL-04) — only while DRAFT. */
public record UpdatePolicyCommand(String holderName, String productCode, LocalDate effectiveDate, LocalDate expiryDate) {}
