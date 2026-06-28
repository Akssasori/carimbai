package com.app.carimbai.execption;

/**
 * Lancada quando o login excede o limite de tentativas (5/15min por email+IP).
 * `retryAfterSeconds` deve virar header `Retry-After` na resposta 429.
 */
public class LoginRateLimitedException extends RuntimeException {
    private final long retryAfterSeconds;

    public LoginRateLimitedException(long retryAfterSeconds) {
        super("Too many login attempts. Try again in " + retryAfterSeconds + " seconds.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
