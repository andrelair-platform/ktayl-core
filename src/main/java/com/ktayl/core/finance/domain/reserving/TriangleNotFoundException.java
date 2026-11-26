package com.ktayl.core.finance.domain.reserving;

/** No loss triangle stored for the requested (portfolio, as-of, basis) → 404. */
public class TriangleNotFoundException extends RuntimeException {
    public TriangleNotFoundException(String portfolio, String asOf, String basis) {
        super("no loss triangle for portfolio=" + portfolio + " as-of=" + asOf + " basis=" + basis);
    }
}
