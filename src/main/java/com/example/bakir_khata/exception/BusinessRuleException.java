package com.example.bakir_khata.exception;

/** Thrown when an action violates a domain business rule (e.g. overpayment). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
