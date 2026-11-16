package com.ktayl.core.screening.domain;

/** The outcome of a screening: {@link #ALLOW} (no hit) or {@link #HOLD} (a hit, or a fail-closed outage). */
public enum Verdict {
    ALLOW,
    HOLD
}
