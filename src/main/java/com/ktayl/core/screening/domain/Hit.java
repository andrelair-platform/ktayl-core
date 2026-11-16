package com.ktayl.core.screening.domain;

/**
 * One match returned by the screening engine — a bucketed score (0–100) against a named list entry. Pure
 * domain; the engine adapter maps the raw provider result into this shape.
 */
public record Hit(Bucket bucket, int score, String matchedEntry) {
}
