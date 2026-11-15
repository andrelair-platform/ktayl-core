package com.ktayl.core.shared.config;

import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Multi-issuer allowlist (ADR-016). ktayl-core validates a JWT's <em>signature</em> against the shared
 * Authentik JWKS (every Authentik provider here signs with the same key); this validator adds back the
 * {@code iss} defence-in-depth that strict {@code issuer-uri} gave — but for a <em>set</em> of trusted
 * providers: ktayl-core's own (human SSO) plus each M2M consumer (e.g. underwriting's bind client).
 *
 * <p>A token whose {@code iss} is not in the allowlist is rejected even when its signature is valid, so a
 * token minted for a <em>different</em> service can never be replayed at ktayl-core. Each new M2M consumer
 * adds its <em>exact</em> issuer string to {@code AUTHENTIK_TRUSTED_ISSUERS} — including the trailing slash
 * and the internal-host form (e.g. {@code http://authentik-server.authentik.svc:80/application/o/<slug>/})
 * that an in-cluster M2M token carries, which differs from the external {@code https://auth.devandre.sbs/…}
 * host a browser SSO token carries.
 */
final class TrustedIssuerValidator implements OAuth2TokenValidator<Jwt> {

    private final Set<String> trusted;

    TrustedIssuerValidator(Set<String> trusted) {
        this.trusted = Set.copyOf(trusted);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        // the RAW claim string (not jwt.getIssuer() → URL, whose toString() can normalise the trailing slash)
        String issuer = jwt.getClaimAsString("iss");
        if (issuer != null && trusted.contains(issuer)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "issuer '" + issuer + "' is not in the trusted-issuer allowlist",
                null));
    }
}
