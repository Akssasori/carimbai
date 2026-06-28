package com.app.carimbai.security;

import java.time.Duration;

/**
 * Sinaliza que o staff está em janela de bloqueio por excesso de PINs incorretos
 * (FIX-04 / SEC-017). Tratada pelo {@code GlobalExceptionHandler} como 423 Locked.
 */
public class PinLockedException extends RuntimeException {

    private final Duration retryAfter;

    public PinLockedException(Duration retryAfter) {
        super("PIN temporariamente bloqueado por excesso de tentativas");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
