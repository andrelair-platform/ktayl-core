package com.ktayl.core.shared.config;

import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authentik OIDC resource-server security. Every API is authenticated except the actuator health/info/
 * prometheus probes and the Stripe M2M webhook — the webhook is NOT SSO-gated (Stripe has no OIDC token)
 * and is instead guarded by Stripe signature verification + idempotent handling (added in BILL-013b).
 *
 * <p>Fine-grained authorization is done per module with {@code @PreAuthorize} (method security enabled
 * here) against the Authentik {@code groups} claim: this config knows only HOW to turn groups into
 * {@code GROUP_<name>} authorities; each module declares WHICH group it requires (e.g. the complaints
 * controller needs {@code GROUP_Complaints Handler}).
 *
 * <p><b>Token validation (ADR-016, multi-issuer):</b> tokens are validated by <em>signature</em> against the
 * shared Authentik JWKS ({@code jwk-set-uri}) — every Authentik provider here signs with the same key — and
 * then the {@code iss} claim is checked against a trusted-issuer <b>allowlist</b> ({@link TrustedIssuerValidator}).
 * This replaces strict {@code issuer-uri}, which rejected a valid M2M token minted by a <em>different</em>
 * Authentik provider (the first such consumer = underwriting's policy-bind client). The allowlist keeps the
 * {@code iss} defence-in-depth (a token for another service can't be replayed here) while admitting the known
 * M2M consumers; it is env-driven ({@code AUTHENTIK_TRUSTED_ISSUERS}) so a new consumer is a config change.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Validate by the shared Authentik JWKS signature + a trusted-issuer allowlist (ADR-016), NOT strict
     * {@code issuer-uri}. {@code JwtValidators.createDefault()} supplies the timestamp (exp/nbf) checks;
     * {@link TrustedIssuerValidator} supplies the {@code iss} allowlist. {@code withJwkSetUri} is lazy (no
     * eager discovery at startup — the JWKS is fetched on first decode).
     */
    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${ktayl.security.trusted-issuers}") Set<String> trustedIssuers) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), new TrustedIssuerValidator(trustedIssuers)));
        return decoder;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                            .permitAll()
                        // M2M PSP callback — signature-verified, not SSO-gated (see BILL-013b)
                        .requestMatchers("/webhooks/stripe").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    /**
     * Map the Authentik {@code groups} claim (a list of group names) to Spring authorities prefixed
     * {@code GROUP_}, so {@code @PreAuthorize("hasAuthority('GROUP_<name>')")} gates by group membership.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::groupsToAuthorities);
        return converter;
    }

    private static List<GrantedAuthority> groupsToAuthorities(Jwt jwt) {
        List<String> groups = jwt.getClaimAsStringList("groups");
        if (groups == null) {
            return List.of();
        }
        return groups.stream().map(g -> (GrantedAuthority) new SimpleGrantedAuthority("GROUP_" + g)).toList();
    }
}
