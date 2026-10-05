package com.ktayl.core.shared.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authentik OIDC resource-server security. Every API is authenticated except the actuator health/info
 * probes and the Stripe M2M webhook — the webhook is NOT SSO-gated (Stripe has no OIDC token) and is
 * instead guarded by Stripe signature verification + idempotent handling (added in BILL-013b).
 *
 * <p>The JWT issuer is configured via {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}
 * ({@code AUTHENTIK_ISSUER}).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // M2M PSP callback — signature-verified, not SSO-gated (see BILL-013b)
                        .requestMatchers("/webhooks/stripe").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
        return http.build();
    }
}
