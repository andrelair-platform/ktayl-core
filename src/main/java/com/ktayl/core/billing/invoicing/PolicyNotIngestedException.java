package com.ktayl.core.billing.invoicing;

/** Raised when an invoice is requested for a policy that has not been ingested (→ 404 at the API). */
public class PolicyNotIngestedException extends RuntimeException {
    public PolicyNotIngestedException(String policyNumber) {
        super("policy not ingested: " + policyNumber);
    }
}
