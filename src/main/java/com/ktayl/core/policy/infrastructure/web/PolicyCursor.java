package com.ktayl.core.policy.infrastructure.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * The opaque keyset cursor for list pagination (BF-POL-04), carried forward from the PAS:
 * {@code base64("<ISO-8601 createdAt>|<uuid>")}. Only ktayl-core encodes/decodes it — a client treats it as
 * opaque. A malformed cursor raises {@link IllegalArgumentException} → the handler maps it to {@code 400}.
 */
final class PolicyCursor {

    final Instant createdAt;
    final UUID id;

    private PolicyCursor(Instant createdAt, UUID id) {
        this.createdAt = createdAt;
        this.id = id;
    }

    static String encode(Instant createdAt, UUID id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((createdAt.toString() + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    static PolicyCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.indexOf('|');
            if (sep < 0) {
                throw new IllegalArgumentException("invalid cursor");
            }
            return new PolicyCursor(Instant.parse(raw.substring(0, sep)), UUID.fromString(raw.substring(sep + 1)));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid cursor");
        }
    }
}
