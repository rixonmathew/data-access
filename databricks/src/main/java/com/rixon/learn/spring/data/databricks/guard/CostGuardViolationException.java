package com.rixon.learn.spring.data.databricks.guard;

/**
 * Thrown whenever an operation violates Databricks Free-Tier budget or safety guardrails.
 */
public class CostGuardViolationException extends RuntimeException {

    private final String violationCode;

    public CostGuardViolationException(String violationCode, String message) {
        super(String.format("[%s] %s", violationCode, message));
        this.violationCode = violationCode;
    }

    public String getViolationCode() {
        return violationCode;
    }
}
