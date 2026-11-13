package com.ktayl.core.complaints.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event — a complaint was registered. Published in-process (ADR-007: Spring {@code ApplicationEvent},
 * not NATS, between modules). Pure data: a plain record, no framework annotations. Other modules that care
 * (e.g. a future statutory-deadline watcher) subscribe via an in-module {@code @ApplicationModuleListener}.
 */
public record ComplaintRegistered(UUID complaintId, ComplaintChannel channel, Instant registeredAt) {
}
