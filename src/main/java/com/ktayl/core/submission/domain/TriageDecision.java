package com.ktayl.core.submission.domain;

/**
 * The outcome of a triage on a submission. {@code ROUTE} moves a RECEIVED submission to TRIAGED (routable);
 * {@code DECLINE} moves it to DECLINED; {@code RETURN} records a request back to the broker for more
 * information (no status change in SUB-01). Pure domain value — no framework.
 */
public enum TriageDecision {
    ROUTE,
    DECLINE,
    RETURN
}
