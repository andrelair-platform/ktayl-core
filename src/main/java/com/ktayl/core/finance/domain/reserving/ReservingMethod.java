package com.ktayl.core.finance.domain.reserving;

/** The actuarial method used to project ultimate losses (FIN-03). */
public enum ReservingMethod {
    /** Volume-weighted development-factor projection (Chain-Ladder / link-ratio). */
    CHAIN_LADDER,
    /** Bornhuetter-Ferguson — blends the chain-ladder pattern with an a-priori expected ultimate. */
    BORNHUETTER_FERGUSON
}
