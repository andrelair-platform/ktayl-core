package com.ktayl.core.screening.domain;

/**
 * The match category of an alert (LCB-FT). S1 only produces {@link #SANCTION} (we load sanctions lists); the
 * other buckets are reserved for later increments but the type already carries them so the schema is stable.
 */
public enum Bucket {
    SANCTION,
    PEP,
    AME,
    RISK_COUNTRY,
    UBO,
    ORIAS
}
