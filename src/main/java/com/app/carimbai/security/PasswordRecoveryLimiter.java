package com.app.carimbai.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Account limit complements the public endpoint's per-IP limit without disclosing accounts. */
@Component
public class PasswordRecoveryLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean allow(String email) {
        String key = OpaqueTokens.hash(email.trim().toLowerCase(Locale.ROOT));
        if (windows.size() >= 10000 && !windows.containsKey(key)) return false;
        Instant now = Instant.now();
        AtomicBoolean allowed = new AtomicBoolean();
        windows.compute(key, (ignored, previous) -> {
            Window window = previous == null || !previous.expires().isAfter(now)
                    ? new Window(now.plusSeconds(900), 0) : previous;
            if (window.attempts() >= 5) return window;
            allowed.set(true);
            return new Window(window.expires(), window.attempts() + 1);
        });
        return allowed.get();
    }

    @Scheduled(fixedDelay = 60000)
    public void deleteExpired() {
        Instant now = Instant.now();
        windows.entrySet().removeIf(entry -> !entry.getValue().expires().isAfter(now));
    }

    private record Window(Instant expires, int attempts) {}
}
