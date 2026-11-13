package com.ktayl.core.shared.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
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
 * <p>The JWT issuer is {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} ({@code AUTHENTIK_ISSUER}).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

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
