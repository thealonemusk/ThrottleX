package com.throttlex.exception;

public class PolicyNotFoundException extends RuntimeException {
    public PolicyNotFoundException(String key) {
        super("No policy found for key: " + key);
    }
}
