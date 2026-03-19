package com.app.carimbai.services;

import com.app.carimbai.models.fidelity.PushSubscription;
import com.app.carimbai.repositories.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final PushService pushService;
    private final PushSubscriptionRepository subscriptionRepo;

    public void sendToCustomer(Long customerId, String title, String body) {
        List<PushSubscription> subscriptions = subscriptionRepo.findByCustomerId(customerId);
        for (PushSubscription sub : subscriptions) {
            Thread.ofVirtual().start(() -> sendSingle(sub, customerId, title, body));
        }
    }

    private void sendSingle(PushSubscription sub, Long customerId, String title, String body) {
        try {
            String payload = """
                    {"title":"%s","body":"%s"}""".formatted(
                    escapeJson(title), escapeJson(body));

            Notification notification = new Notification(
                    sub.getEndpoint(),
                    sub.getP256dh(),
                    sub.getAuth(),
                    payload
            );

            HttpResponse response = pushService.send(notification);
            int status = response.getStatusLine().getStatusCode();

            if (status == 410 || status == 404) {
                cleanupExpiredSubscription(sub.getEndpoint());
            }
        } catch (Exception e) {
            log.warn("Push falhou para customer={}: {}", customerId, e.getMessage());
        }
    }

    private void cleanupExpiredSubscription(String endpoint) {
        subscriptionRepo.deleteByEndpoint(endpoint);
        log.info("Subscription expirada removida: {}", endpoint);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
