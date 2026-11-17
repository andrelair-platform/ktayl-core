package com.ktayl.core.submission.domain;

/**
 * A domain-rule violation (e.g. a blank insured name, a missing product line, a non-positive TIV). Pure
 * domain — carries no HTTP/framework concept; the web adapter maps it to a 4xx (SUB-01).
 */
public class InvalidSubmissionException extends RuntimeException {
    public InvalidSubmissionException(String message) {
        super(message);
    }
}
