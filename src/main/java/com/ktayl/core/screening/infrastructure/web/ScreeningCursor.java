package com.ktayl.core.screening.infrastructure.web;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * The opaque keyset cursor for the alerts list — {@code base64("<uuid>")} (alerts page by id). Mirrors the
 * policy module's cursor pattern; a client treats it as opaque. A malformed cursor raises
 * {@link IllegalArgumentException} → the handler maps it to {@code 400}.
 */
final class ScreeningCursor {

    private ScreeningCursor() {}

    static String encode(UUID id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(id.toString().getBytes(StandardCharsets.UTF_8));
    }

    static UUID decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return UUID.fromString(raw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid cursor");
        }
    }
}
