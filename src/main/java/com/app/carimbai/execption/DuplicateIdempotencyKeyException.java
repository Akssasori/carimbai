package com.app.carimbai.execption;

public class DuplicateIdempotencyKeyException extends RuntimeException {
    public DuplicateIdempotencyKeyException(String message) {
        super("Duplicate Idempotency-key" + message);
    }
}
