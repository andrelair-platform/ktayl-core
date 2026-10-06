package com.ktayl.core.billing.ingest;

/**
 * Domain event — a bound policy's premium was ingested (BILL-011). Published AFTER the ingest commits so
 * invoicing (BILL-012) can auto-raise the invoice in its own transaction (decoupled: an invoicing failure
 * never rolls back the ingest). In-process Spring application event within the billing module.
 */
public record PolicyIngested(String policyNumber) {}
