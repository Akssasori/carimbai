package com.app.carimbai.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Rate limit por IP por endpoint (FIX-04 / SEC-006). In-memory (Bucket4j) — adequado
 * para uma única instância na VPS atual; se escalar para múltiplos nós, migrar
 * para storage distribuído (Redis).
 *
 * <p>Aplica antes da auth, para que tentativas de brute-force em /login não
 * encham logs/banco. Endpoints autenticados pesados (cards/qr) também são
 * limitados por IP.
 *
 * <p>Resposta de bloqueio: 429 com cabeçalho {@code Retry-After}.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private record Rule(HttpMethod method, String pathPattern, String name, long capacity, Duration window) {
        Bucket newBucket() {
            return Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(capacity)
                            .refillGreedy(capacity, window)
                            .build())
                    .build();
        }
    }

    // Limites conservadores (recomendados na proposta FIX-04).
    private static final List<Rule> RULES = List.of(
            new Rule(HttpMethod.POST, "/api/auth/login", "auth-login", 10, Duration.ofMinutes(1)),
            new Rule(HttpMethod.POST, "/api/customers/social-login", "social-login", 10, Duration.ofMinutes(1)),
            new Rule(HttpMethod.POST, "/api/customers/login-or-register", "login-or-register", 10, Duration.ofMinutes(1)),
            new Rule(HttpMethod.POST, "/api/cards", "enroll", 30, Duration.ofMinutes(1)),
            new Rule(HttpMethod.GET, "/api/qr/**", "qr", 60, Duration.ofMinutes(1)),
            new Rule(HttpMethod.GET, "/api/cards/*/redeem-qr", "redeem-qr", 60, Duration.ofMinutes(1))
    );

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Rule rule = matchRule(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        String key = rule.name() + "|" + ip;
        Bucket bucket = buckets.computeIfAbsent(key, k -> rule.newBucket());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfterSec = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
            log.warn("Rate limit hit: rule={} ip={} retryAfter={}s", rule.name(), ip, retryAfterSec);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSec));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limited\",\"retryAfter\":" + retryAfterSec + "}");
            return;
        }
        chain.doFilter(request, response);
    }

    private Rule matchRule(HttpServletRequest req) {
        String method = req.getMethod();
        String path = req.getRequestURI();
        for (Rule r : RULES) {
            if (r.method().name().equals(method) && MATCHER.match(r.pathPattern(), path)) {
                return r;
            }
        }
        return null;
    }

    private static String clientIp(HttpServletRequest req) {
        // server.forward-headers-strategy=framework já trata X-Forwarded-* antes de chegar aqui;
        // mas mantemos fallback explícito caso o proxy injete só X-Forwarded-For.
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }
}
