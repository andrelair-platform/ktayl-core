package com.ktayl.core.complaints.domain;

/**
 * A domain-rule violation (e.g. a blank summary, an illegal state transition). Pure domain — carries no
 * HTTP/framework concept; the web adapter maps it to a 4xx (CMPL-013).
 */
public class InvalidComplaintException extends RuntimeException {
    public InvalidComplaintException(String message) {
        super(message);
    }
}
