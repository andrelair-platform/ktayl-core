package com.ktayl.core.finance.domain.reserving;

/**
 * A segregation-of-duties breach (FIN-03, threat T7): the person approving/rejecting a reserve review is the
 * same person who submitted it. Four-eyes requires the checker to differ from the maker → the transition is
 * refused (→ 409).
 */
public class SelfApprovalException extends RuntimeException {
    public SelfApprovalException(String actor) {
        super("segregation of duties: " + actor + " submitted this reserve review and cannot also decide it");
    }
}
