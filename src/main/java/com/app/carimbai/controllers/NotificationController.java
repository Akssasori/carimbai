package com.app.carimbai.controllers;

import com.app.carimbai.dtos.PushSubscribeRequest;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.models.fidelity.PushSubscription;
import com.app.carimbai.repositories.CustomerRepository;
import com.app.carimbai.repositories.PushSubscriptionRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final PushSubscriptionRepository subscriptionRepo;
    private final CustomerRepository customerRepo;

    @Value("${carimbai.vapid.public-key}")
    private String vapidPublicKey;

    @Operation(summary = "Subscribe to push notifications",
            description = "Registers the browser push subscription for the given customer.")
    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscribeRequest request) {
        Customer customer = customerRepo.findById(request.customerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + request.customerId()));

        boolean exists = subscriptionRepo
                .findByCustomerIdAndEndpoint(customer.getId(), request.endpoint())
                .isPresent();

        if (!exists) {
            PushSubscription subscription = PushSubscription.builder()
                    .customer(customer)
                    .endpoint(request.endpoint())
                    .p256dh(request.keys().p256dh())
                    .auth(request.keys().auth())
                    .build();
            subscriptionRepo.save(subscription);
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Get VAPID public key",
            description = "Returns the VAPID public key for the frontend to configure PushManager.")
    @GetMapping("/vapid-public-key")
    public ResponseEntity<Map<String, String>> getVapidPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", vapidPublicKey));
    }
}
