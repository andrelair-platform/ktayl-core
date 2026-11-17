package com.ktayl.core.submission.infrastructure.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * The opaque keyset cursor for the submissions list — {@code base64("<receivedAt>|<uuid>")} (submissions page
 * by {@code (received_at, id)}). Mirrors the policy / screening cursor pattern; a client treats it as opaque.
 * A malformed cursor raises {@link IllegalArgumentException} → the handler maps it to {@code 400}.
 */
final class SubmissionCursor {

    private final Instant receivedAt;
    private final UUID id;

    private SubmissionCursor(Instant receivedAt, UUID id) {
        this.receivedAt = receivedAt;
        this.id = id;
    }

    Instant receivedAt() { return receivedAt; }

    UUID id() { return id; }

    static String encode(Instant receivedAt, UUID id) {
        String raw = receivedAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static SubmissionCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.lastIndexOf('|');
            return new SubmissionCursor(Instant.parse(raw.substring(0, sep)),
                    UUID.fromString(raw.substring(sep + 1)));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid cursor");
        }
    }
}
