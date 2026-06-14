package com.app.carimbai.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Denylist de JWTs revogados, indexada por {@code jti} (FIX-11 / SEC-012).
 * In-memory — adequado para single-node; migrar para Redis quando escalar.
 *
 * <p>Entradas expiram quando o próprio token expiraria — a partir daí o
 * filtro já rejeitaria por TTL e a entrada vira lixo. A limpeza é preguiçosa
 * (no próprio {@link #isRevoked(String)}); para single-node isso basta.
 */
@Service
public class TokenRevocationService {

    private final ConcurrentHashMap<String, Instant> denylist = new ConcurrentHashMap<>();

    public void revoke(String jti, Instant exp) {
        if (jti == null || exp == null) return;
        denylist.put(jti, exp);
    }

    public boolean isRevoked(String jti) {
        if (jti == null) return false;
        Instant exp = denylist.get(jti);
        if (exp == null) return false;
        if (exp.isBefore(Instant.now())) {
            denylist.remove(jti); // GC preguiçoso
            return false;
        }
        return true;
    }

    /** Visível só para testes. */
    int size() {
        return denylist.size();
    }
}
