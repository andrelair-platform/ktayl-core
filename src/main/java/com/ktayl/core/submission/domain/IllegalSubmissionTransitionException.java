package com.ktayl.core.submission.domain;

/**
 * A lifecycle transition attempted from the wrong state (e.g. route before triage, or any operation on a
 * DECLINED/ROUTED submission). Pure domain — the web adapter (SUB-01) maps it to HTTP <b>409 Conflict</b>
 * (distinct from {@link InvalidSubmissionException} = bad input → 4xx).
 */
public class IllegalSubmissionTransitionException extends RuntimeException {
    public IllegalSubmissionTransitionException(SubmissionStatus from, SubmissionStatus required) {
        super("illegal transition: submission is " + from + " but this operation requires " + required);
    }
}
