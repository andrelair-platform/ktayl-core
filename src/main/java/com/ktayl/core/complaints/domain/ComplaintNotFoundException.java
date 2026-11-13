package com.ktayl.core.complaints.domain;

import java.util.UUID;

/**
 * No complaint exists for the given id. Pure domain — the web adapter (CMPL-013) maps it to HTTP
 * <b>404 Not Found</b>.
 */
public class ComplaintNotFoundException extends RuntimeException {
    public ComplaintNotFoundException(UUID id) {
        super("complaint not found: " + id);
    }
}
