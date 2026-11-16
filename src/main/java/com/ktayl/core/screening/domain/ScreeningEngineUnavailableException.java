package com.ktayl.core.screening.domain;

/**
 * The screening engine could not be reached or errored. Pure domain — carries no HTTP/framework concept. The
 * engine adapter throws it on any connection/HTTP failure; the use case turns it into a fail-closed HOLD
 * (LCB-FT: never silent-ALLOW when the check is down).
 */
public class ScreeningEngineUnavailableException extends RuntimeException {
    public ScreeningEngineUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ScreeningEngineUnavailableException(String message) {
        super(message);
    }
}
