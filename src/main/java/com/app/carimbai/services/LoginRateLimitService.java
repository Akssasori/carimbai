package com.app.carimbai.services;

import com.app.carimbai.execption.LoginRateLimitedException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limit em memoria para o POST /api/auth/login.
 *
 * Algoritmo: token bucket (Bucket4j).
 * Janela: 5 tentativas por 15 minutos por (email lowercased + ip).
 *
 * Em memoria → single-instance. Se voce escalar para multi-instancia,
 * troca-se a fonte do bucket por Redis sem mudar a API publica deste service.
 *
 * Tentativas SUCEDIDAS NAO contam para o limite: o `recordSuccess` reseta o bucket
 * daquela chave (assim quem digitou senha errada e acertou em seguida nao "carrega"
 * o consumo restante).
 */
@Service
public class LoginRateLimitService {

    @Value("${carimbai.login-rate-limit.max-attempts:5}")
    private int maxAttempts;

    @Value("${carimbai.login-rate-limit.window-minutes:15}")
    private int windowMinutes;

    @Autowired(required = false)
    private HttpServletRequest request;

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Verifica se a tentativa pode prosseguir. Se sim, decrementa 1 token do bucket.
     * Se nao, joga LoginRateLimitedException com o tempo de espera.
     */
    public void checkOrThrow(String email) {
        String key = buildKey(email);
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retrySeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            throw new LoginRateLimitedException(retrySeconds);
        }
    }

    /**
     * Login bem-sucedido: zera o bucket daquela chave para nao prejudicar futuras
     * tentativas (caso o usuario tenha gasto 3 de 5 antes de acertar).
     */
    public void recordSuccess(String email) {
        buckets.remove(buildKey(email));
    }

    private String buildKey(String email) {
        String normalizedEmail = email == null ? "" : email.toLowerCase(Locale.ROOT).trim();
        String ip = resolveIp();
        return normalizedEmail + "|" + ip;
    }

    private String resolveIp() {
        if (request == null) return "unknown";
        try {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String remote = request.getRemoteAddr();
            return remote != null ? remote : "unknown";
        } catch (IllegalStateException e) {
            return "unknown";
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(maxAttempts)
                .refillIntervally(maxAttempts, Duration.ofMinutes(windowMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
