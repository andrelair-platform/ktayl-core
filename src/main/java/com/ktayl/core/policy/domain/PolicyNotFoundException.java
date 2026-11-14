package com.ktayl.core.policy.domain;

/** No policy with that id / policy_number — maps to 404. */
public class PolicyNotFoundException extends RuntimeException {
    public PolicyNotFoundException(String message) {
        super(message);
    }
}
