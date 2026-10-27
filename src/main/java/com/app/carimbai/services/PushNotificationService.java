package com.app.carimbai.services;

import com.app.carimbai.models.PushSubscription;
import com.app.carimbai.security.PushEndpointPolicy;
import com.app.carimbai.security.RestrictedPushService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
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
    private final PushEndpointPolicy endpointPolicy;

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
                pushService = new RestrictedPushService(vapidPublicKey, vapidPrivateKey, vapidSubject, endpointPolicy);
                log.info("PushService initialized");
            } catch (Exception e) {
                log.error("Push initialization failed: {}. Notifications disabled.", e.getClass().getSimpleName());
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
            if (!subscriptionService.canSend(sub.getCustomer().getId())) return;
            endpointPolicy.validateDestination(sub.getEndpoint());
            endpointPolicy.validateKeys(sub.getP256dh(), sub.getAuth());
            Subscription.Keys keys = new Subscription.Keys(sub.getP256dh(), sub.getAuth());
            Subscription subscription = new Subscription(sub.getEndpoint(), keys);
            Notification notification = new Notification(subscription, payload);

            org.apache.http.HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 404 || statusCode == 410) {
                // Subscription morta — limpa do banco.
                log.info("Subscription gone (status={}) for customer={}", statusCode, sub.getCustomer().getId());
                subscriptionService.delete(sub);
            } else if (statusCode >= 400) {
                log.warn("Push delivery failed with status={} for customer={}", statusCode, sub.getCustomer().getId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Push delivery interrupted");
        } catch (Exception e) {
            log.warn("Push delivery failed: {}", e.getClass().getSimpleName());
        }
    }

    private String serializePayload(String title, String body) {
        try {
            return objectMapper.writeValueAsString(Map.of("title", title, "body", body));
        } catch (JacksonException e) {
            log.error("Push payload serialization failed");
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

}
