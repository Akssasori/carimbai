package com.app.carimbai.security;

import org.junit.jupiter.api.Test;
import com.app.carimbai.repositories.RevokedTokenRepository;
import com.app.carimbai.models.RevokedToken;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** FIX-11 / SEC-012 — denylist por jti. */
class TokenRevocationServiceTest {
    private final RevokedTokenRepository repository = mock(RevokedTokenRepository.class);

    @Test
    void notRevoked_byDefault() {
        var s = new TokenRevocationService(repository);
        assertThat(s.isRevoked("jti-1")).isFalse();
    }

    @Test
    void revoke_markedAsRevoked() {
        var s = new TokenRevocationService(repository);
        Instant expiry = Instant.now().plusSeconds(60);
        s.revoke("jti-1", expiry);
        verify(repository).revokeIfAbsent("jti-1", expiry);
        when(repository.existsByJtiAndExpiresAtAfter(eq("jti-1"), any())).thenReturn(true);
        assertThat(s.isRevoked("jti-1")).isTrue();
        assertThat(new TokenRevocationService(repository).isRevoked("jti-1")).isTrue();
    }

    @Test
    void expiredEntry_isGarbageCollected() {
        var s = new TokenRevocationService(repository);
        s.revoke("jti-1", Instant.now().minusSeconds(1)); // já passou
        assertThat(s.isRevoked("jti-1")).isFalse();
        verify(repository, never()).revokeIfAbsent(any(), any());
        s.deleteExpired();
        verify(repository).deleteByExpiresAtBefore(any());
    }

    @Test
    void nullJti_returnsFalse_andDoesNothing() {
        var s = new TokenRevocationService(repository);
        assertThat(s.isRevoked(null)).isFalse();
        s.revoke(null, Instant.now().plusSeconds(60));
        verifyNoInteractions(repository);
    }
}
