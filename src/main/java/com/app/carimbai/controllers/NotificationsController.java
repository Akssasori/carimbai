package com.app.carimbai.controllers;

import com.app.carimbai.dtos.notifications.SubscribePushRequest;
import com.app.carimbai.dtos.notifications.VapidPublicKeyResponse;
import com.app.carimbai.services.PushNotificationService;
import com.app.carimbai.services.PushSubscriptionService;
import com.app.carimbai.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationsController {

    private final PushNotificationService pushNotificationService;
    private final PushSubscriptionService subscriptionService;

    @Operation(summary = "Devolve a VAPID public key para o navegador do cliente.",
            description = "Publico (sem auth) — o cliente precisa dela antes de pedir permissao.")
    @GetMapping("/vapid-public-key")
    public ResponseEntity<VapidPublicKeyResponse> vapidPublicKey() {
        return ResponseEntity.ok(new VapidPublicKeyResponse(pushNotificationService.getPublicKey()));
    }

    @Operation(summary = "Inscreve um device do cliente para receber push notifications.",
            description = "Idempotente: (customer, endpoint) e UNIQUE. customerId vem do JWT, body e ignorado para isso.")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody SubscribePushRequest request) {
        Long customerId = SecurityUtils.getRequiredCustomerId();
        subscriptionService.subscribe(
                customerId,
                request.endpoint(),
                request.keys().p256dh(),
                request.keys().auth()
        );
        return ResponseEntity.noContent().build();
    }
}
