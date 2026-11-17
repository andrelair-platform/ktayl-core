package com.ktayl.core.submission.domain;

import java.util.UUID;

/**
 * No submission exists for the given id. Pure domain — the web adapter (SUB-01) maps it to HTTP
 * <b>404 Not Found</b>.
 */
public class SubmissionNotFoundException extends RuntimeException {
    public SubmissionNotFoundException(UUID id) {
        super("submission not found: " + id);
    }
}
