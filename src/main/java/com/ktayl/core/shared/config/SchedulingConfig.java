package com.ktayl.core.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled tasks (the GL outbox drainer, BILL-014). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
