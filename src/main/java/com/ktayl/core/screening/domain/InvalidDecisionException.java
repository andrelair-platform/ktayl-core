package com.ktayl.core.screening.domain;

/**
 * A HITL decision breaks a domain rule (e.g. an ACC/REF decision without a required grade). Pure domain —
 * carries no HTTP/framework concept; the web adapter maps it to a 422.
 */
public class InvalidDecisionException extends RuntimeException {
    public InvalidDecisionException(String message) {
        super(message);
    }
}
