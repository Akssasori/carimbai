package com.app.carimbai.security;

import com.app.carimbai.repositories.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

/**
 * Denylist de JWTs revogados, indexada por {@code jti} (FIX-11 / SEC-012).
 * Persistida no PostgreSQL e compartilhada entre instancias da aplicacao.
 *
 * <p>Entradas expiram quando o próprio token expiraria — a partir daí o
 * filtro ja rejeitaria por TTL. Uma tarefa agendada remove entradas expiradas.
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final RevokedTokenRepository revokedTokens;

    @Transactional
    public boolean revoke(String jti, Instant exp) {
        if (jti == null || exp == null || !exp.isAfter(Instant.now())) return false;
        return revokedTokens.revokeIfAbsent(jti, exp)>0;
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String jti) {
        return jti != null && revokedTokens.existsByJtiAndExpiresAtAfter(jti, Instant.now());
    }

    @Scheduled(fixedDelayString = "${carimbai.jwt.revocation-cleanup-ms:3600000}")
    @Transactional
    public void deleteExpired() {
        revokedTokens.deleteByExpiresAtBefore(Instant.now());
    }
}
