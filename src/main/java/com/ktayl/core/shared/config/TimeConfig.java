package com.ktayl.core.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a single injectable {@link Clock} so time is a dependency, not a static call. Domain/application
 * code takes {@code Clock} and reads {@code Instant.now(clock)} — a test injects a fixed clock to assert on
 * timestamps deterministically (hexagonal testability, ADR-013). Lives in the OPEN {@code shared} module so
 * every business module can wire it.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
