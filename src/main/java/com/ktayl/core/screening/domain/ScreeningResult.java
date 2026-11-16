package com.ktayl.core.screening.domain;

import java.util.List;

/**
 * The result of a single engine call: the dataset version matched against + the list of hits. The
 * {@link #verdict()} is derived purely — any hit means {@link Verdict#HOLD}, an empty list means
 * {@link Verdict#ALLOW}. This is the core business rule and it lives in the domain (no framework).
 */
public record ScreeningResult(String datasetVersion, List<Hit> hits) {

    public ScreeningResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }

    /** ALLOW when there are no hits, otherwise HOLD. */
    public Verdict verdict() {
        return hits.isEmpty() ? Verdict.ALLOW : Verdict.HOLD;
    }
}
