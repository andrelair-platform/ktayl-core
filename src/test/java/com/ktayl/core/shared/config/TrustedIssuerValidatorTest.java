package com.ktayl.core.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** L1 — the multi-issuer allowlist (ADR-016): accept a trusted iss, reject anything else or a missing iss. */
class TrustedIssuerValidatorTest {

    private static final String CORE = "https://auth.devandre.sbs/application/o/ktayl-core/";
    // The REAL in-cluster M2M token iss — NO `:80` (Authentik drops the default port from the request Host).
    // A `:80` here was the 2026-11-17 bug: it failed the exact match against the real token → 401.
    private static final String UW = "http://authentik-server.authentik.svc/application/o/ktayl-underwriting/";

    private final TrustedIssuerValidator validator = new TrustedIssuerValidator(Set.of(CORE, UW));

    private static Jwt tokenWithIssuer(String iss) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").claim("sub", "s")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        if (iss != null) {
            b.claim("iss", iss);
        }
        return b.build();
    }

    @Test
    void accepts_the_human_sso_issuer() {
        assertThat(validator.validate(tokenWithIssuer(CORE)).hasErrors()).isFalse();
    }

    @Test
    void accepts_the_underwriting_m2m_issuer() {
        assertThat(validator.validate(tokenWithIssuer(UW)).hasErrors()).isFalse();
    }

    @Test
    void rejects_an_untrusted_issuer_even_with_a_valid_signature() {
        // a token from another Authentik provider (same signing key) must NOT be replayable here
        assertThat(validator.validate(tokenWithIssuer(
                        "http://authentik-server.authentik.svc:80/application/o/some-other-app/"))
                .hasErrors()).isTrue();
    }

    @Test
    void rejects_a_near_miss_without_the_trailing_slash() {
        assertThat(validator.validate(tokenWithIssuer(CORE.substring(0, CORE.length() - 1))).hasErrors()).isTrue();
    }

    @Test
    void rejects_a_missing_issuer() {
        assertThat(validator.validate(tokenWithIssuer(null)).hasErrors()).isTrue();
    }
}
