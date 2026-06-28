package com.app.carimbai.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lockout de PIN por staffUserId (FIX-04 / SEC-017 brute-force). In-memory —
 * suficiente para single-node; se escalar, migrar para coluna em
 * {@code core.staff_users} + relógio centralizado.
 *
 * <p>Política (recomendada na proposta FIX-04): <strong>5 erros em 10 minutos
 * → bloqueia por 15 minutos</strong>. Acerto reseta a contagem.
 */
@Component
@Slf4j
public class PinLockoutService {

    public static final int MAX_FAILURES = 5;
    public static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
    public static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private static final class Entry {
        final Deque<Instant> failures = new ArrayDeque<>();
        Instant blockedUntil;
    }

    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();

    /** Lança {@link PinLockedException} se o staff estiver em janela de bloqueio. */
    public void assertNotLocked(Long staffUserId) {
        Entry e = entries.get(staffUserId);
        if (e == null) return;
        synchronized (e) {
            if (e.blockedUntil != null && e.blockedUntil.isAfter(Instant.now())) {
                throw new PinLockedException(Duration.between(Instant.now(), e.blockedUntil));
            }
        }
    }

    /** Registra falha; se atingir {@link #MAX_FAILURES} dentro da janela, bloqueia. */
    public void recordFailure(Long staffUserId) {
        Entry e = entries.computeIfAbsent(staffUserId, k -> new Entry());
        synchronized (e) {
            Instant now = Instant.now();
            pruneOld(e, now);
            e.failures.addLast(now);
            if (e.failures.size() >= MAX_FAILURES) {
                e.blockedUntil = now.plus(LOCKOUT_DURATION);
                e.failures.clear();
                log.warn("PIN lockout: staffUserId={} blockedFor={}", staffUserId, LOCKOUT_DURATION);
            }
        }
    }

    /** Reseta contagem e bloqueio em acerto. */
    public void recordSuccess(Long staffUserId) {
        Entry e = entries.get(staffUserId);
        if (e == null) return;
        synchronized (e) {
            e.failures.clear();
            e.blockedUntil = null;
        }
    }

    private static void pruneOld(Entry e, Instant now) {
        Instant cutoff = now.minus(FAILURE_WINDOW);
        for (Iterator<Instant> it = e.failures.iterator(); it.hasNext(); ) {
            if (it.next().isBefore(cutoff)) it.remove();
            else break;
        }
    }
}
