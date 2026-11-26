package com.ktayl.core.finance.domain.reserving;

/**
 * The supplied loss triangle is not a valid upper-left cumulative staircase (empty, ragged, a missing lag, or
 * a cumulative that falls). A client/data error (→ 422), never a server fault.
 */
public class TriangleShapeException extends RuntimeException {
    public TriangleShapeException(String message) {
        super(message);
    }
}
