package com.app.carimbai.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** FIX-11 / SEC-012 — denylist por jti. */
class TokenRevocationServiceTest {

    @Test
    void notRevoked_byDefault() {
        var s = new TokenRevocationService();
        assertThat(s.isRevoked("jti-1")).isFalse();
    }

    @Test
    void revoke_markedAsRevoked() {
        var s = new TokenRevocationService();
        s.revoke("jti-1", Instant.now().plusSeconds(60));
        assertThat(s.isRevoked("jti-1")).isTrue();
    }

    @Test
    void expiredEntry_isGarbageCollected() {
        var s = new TokenRevocationService();
        s.revoke("jti-1", Instant.now().minusSeconds(1)); // já passou
        assertThat(s.isRevoked("jti-1")).isFalse();
        assertThat(s.size()).isZero(); // removeu sozinho
    }

    @Test
    void nullJti_returnsFalse_andDoesNothing() {
        var s = new TokenRevocationService();
        assertThat(s.isRevoked(null)).isFalse();
        s.revoke(null, Instant.now().plusSeconds(60));
        assertThat(s.size()).isZero();
    }
}
