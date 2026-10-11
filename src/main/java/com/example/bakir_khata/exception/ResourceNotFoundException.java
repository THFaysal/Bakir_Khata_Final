package com.example.bakir_khata.exception;

/** Thrown when a requested entity does not exist or does not belong to the current user. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
