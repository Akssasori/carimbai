package com.app.carimbai.services;

import com.app.carimbai.repositories.StaffUserRepository;
import com.app.carimbai.security.audit.AuditEvent;
import com.app.carimbai.security.audit.AuditSecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {
    private final StaffUserRepository staffRepo;
    private final PasswordResetTokenService resetTokens;
    private final MailService mail;
    private final TransactionTemplate transactions;
    private final AuditSecurityService audit;
    private final com.app.carimbai.security.PasswordRecoveryLimiter recoveryLimiter;

    @Value("${carimbai.app-base-url}")
    private String appBaseUrl;

    @PostConstruct
    void validateBaseUrl() {
        URI uri = URI.create(appBaseUrl);
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) && "localhost".equals(uri.getHost()))) {
            throw new IllegalArgumentException("Invalid password recovery base URL");
        }
    }

    // Queue the lookup as well as delivery: the HTTP response must not reveal account existence.
    @Async("mailExecutor")
    public void requestReset(String email) {
        audit.success(AuditEvent.PASSWORD_RESET_REQUEST, Map.of());
        if (!recoveryLimiter.allow(email) || !mail.isReady()) return;
        var delivery = transactions.execute(status -> staffRepo.findByEmailForUpdate(email)
                .filter(staff -> Boolean.TRUE.equals(staff.getActive()))
                .map(staff -> new Delivery(staff.getEmail(), resetTokens.issue(staff).rawToken()))
                .orElse(null));
        if (delivery == null) return;
        String url = UriComponentsBuilder.fromUriString(appBaseUrl).path("/staff/reset-password")
                .queryParam("token", delivery.token()).build().toUriString();
        if (!mail.sendPasswordResetEmail(delivery.email(), null, url)) {
            audit.failure(AuditEvent.PASSWORD_RESET_REQUEST, Map.of("reason", "delivery_failed"));
        }
    }

    private record Delivery(String email, String token) {}
}
