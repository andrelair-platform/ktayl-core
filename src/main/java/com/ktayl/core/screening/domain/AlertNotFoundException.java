package com.ktayl.core.screening.domain;

/** No alert exists for the given id. Pure domain; the web adapter maps it to a 404. */
public class AlertNotFoundException extends RuntimeException {
    public AlertNotFoundException(String message) {
        super(message);
    }
}
