package com.app.carimbai.services;

import com.app.carimbai.repositories.StampTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class StampTokenService {

    private final StampTokenRepository stampTokenRepository;
    private final ThreadLocal<Mac> macThreadLocal;

    private static final Duration TTL = Duration.ofMinutes(45);

    public StampTokenService() {
        this.macThreadLocal = ThreadLocal.withInitial(() -> {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                // Initialize with a secret key (omitted for brevity)
                return mac;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize Mac instance", e);
            }
        });
    }

}
