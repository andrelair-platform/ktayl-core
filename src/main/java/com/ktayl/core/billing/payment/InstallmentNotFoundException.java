package com.ktayl.core.billing.payment;

/** No installment with that id — a 404. */
public class InstallmentNotFoundException extends RuntimeException {
    public InstallmentNotFoundException(String message) {
        super(message);
    }
}
