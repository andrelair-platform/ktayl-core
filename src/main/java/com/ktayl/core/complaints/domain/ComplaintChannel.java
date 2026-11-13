package com.ktayl.core.complaints.domain;

/** The channel a complaint arrived through. Pure domain value — no framework. */
public enum ComplaintChannel {
    PHONE,
    EMAIL,
    PORTAL,
    LETTER,
    BROKER
}
