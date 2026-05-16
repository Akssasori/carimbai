package com.app.carimbai.services;

import com.app.carimbai.models.PushSubscription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Envia push notifications via Web Push Protocol (VAPID).
 *
 * Comportamento:
 *  - @Async via pool "pushExecutor" (config em AsyncConfig). Caller nunca espera.
 *  - Se VAPID nao configurado, faz no-op (loga warning na inicializacao).
 *  - Em 404/410 (subscription gone), deleta a row local. Auto-cleanup.
 */
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final PushSubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    @Value("${carimbai.vapid.public-key:}")
    private String vapidPublicKey;

    @Value("${carimbai.vapid.private-key:}")
    private String vapidPrivateKey;

    @Value("${carimbai.vapid.subject:mailto:contato@example.com}")
    private String vapidSubject;

    private PushService pushService;

    @PostConstruct
    public void init() {
        if (isVapidConfigured()) {
            Security.addProvider(new BouncyCastleProvider());
            try {
                pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
                log.info("PushService inicializado. VAPID subject={}", vapidSubject);
            } catch (Exception e) {
                log.error("Falha ao inicializar PushService: {}. Push notifications desabilitadas.", e.getMessage());
                pushService = null;
            }
        } else {
            log.warn("VAPID keys nao configuradas (CARIMBAI_VAPID_PUBLIC_KEY/PRIVATE_KEY vazias). " +
                    "Push notifications desabilitadas.");
        }
    }

    @Async("pushExecutor")
    public void sendToCustomer(Long customerId, String title, String body) {
        if (pushService == null) return;

        List<PushSubscription> subs = subscriptionService.listByCustomer(customerId);
        if (subs.isEmpty()) return;

        String payload = serializePayload(title, body);
        if (payload == null) return;

        for (PushSubscription sub : subs) {
            sendOne(sub, payload);
        }
    }

    private void sendOne(PushSubscription sub, String payload) {
        try {
            Subscription.Keys keys = new Subscription.Keys(sub.getP256dh(), sub.getAuth());
            Subscription subscription = new Subscription(sub.getEndpoint(), keys);
            Notification notification = new Notification(subscription, payload);

            org.apache.http.HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 404 || statusCode == 410) {
                // Subscription morta — limpa do banco.
                log.info("Subscription gone (status={}) para customer={}, endpoint={}. Deletando.",
                        statusCode, sub.getCustomer().getId(), summarizeEndpoint(sub.getEndpoint()));
                subscriptionService.delete(sub);
            } else if (statusCode >= 400) {
                log.warn("Push falhou com status={} para customer={}, endpoint={}",
                        statusCode, sub.getCustomer().getId(), summarizeEndpoint(sub.getEndpoint()));
            }
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Erro ao enviar push para customer={}: {}",
                    sub.getCustomer().getId(), e.getMessage());
        } catch (Exception e) {
            log.error("Erro inesperado ao enviar push para customer={}: {}",
                    sub.getCustomer().getId(), e.getMessage(), e);
        }
    }

    private String serializePayload(String title, String body) {
        try {
            return objectMapper.writeValueAsString(Map.of("title", title, "body", body));
        } catch (JsonProcessingException e) {
            log.error("Falha ao serializar payload de push: {}", e.getMessage());
            return null;
        }
    }

    private boolean isVapidConfigured() {
        return vapidPublicKey != null && !vapidPublicKey.isBlank()
                && vapidPrivateKey != null && !vapidPrivateKey.isBlank();
    }

    public String getPublicKey() {
        return vapidPublicKey;
    }

    public boolean isReady() {
        return pushService != null;
    }

    private String summarizeEndpoint(String endpoint) {
        if (endpoint == null) return "";
        return endpoint.length() > 60 ? endpoint.substring(0, 60) + "..." : endpoint;
    }
}
