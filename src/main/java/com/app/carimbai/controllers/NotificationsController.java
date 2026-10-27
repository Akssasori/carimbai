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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import com.app.carimbai.dtos.privacy.PrivacyDtos.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationsController {

    private final PushNotificationService pushNotificationService;
    private final PushSubscriptionService subscriptionService;

    @GetMapping("/preferences") @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PushState> preferences() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(subscriptionService.preferences(SecurityUtils.getRequiredCustomerId()));
    }
    @PutMapping("/preferences") @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PushState> choose(@Valid @RequestBody PushChoice input) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(subscriptionService.choose(SecurityUtils.getRequiredCustomerId(),input));
    }
    @DeleteMapping("/subscriptions") @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> unsubscribe() {
        subscriptionService.choose(SecurityUtils.getRequiredCustomerId(),new PushChoice(false,null));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/unsubscribe-device") @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> unsubscribeDevice(@Valid @RequestBody PushDevice input) {
        subscriptionService.unsubscribeDevice(SecurityUtils.getRequiredCustomerId(),input.endpoint());
        return ResponseEntity.noContent().cacheControl(org.springframework.http.CacheControl.noStore()).build();
    }

    @Operation(summary = "Devolve a VAPID public key para o navegador do cliente.",
            description = "Publico (sem auth) — o cliente precisa dela antes de pedir permissao.")
    @GetMapping("/vapid-public-key")
    public ResponseEntity<VapidPublicKeyResponse> vapidPublicKey() {
        if (!pushNotificationService.isReady()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Push is not configured");
        }
        return ResponseEntity.ok(new VapidPublicKeyResponse(pushNotificationService.getPublicKey()));
    }

    @Operation(summary = "Inscreve um device do cliente para receber push notifications.",
            description = "Idempotente: (customer, endpoint) e UNIQUE. customerId vem do JWT, body e ignorado para isso.")
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody SubscribePushRequest request) {
        Long customerId = SecurityUtils.getRequiredCustomerId();
        if (request.customerId() != null && !request.customerId().equals(customerId)) {
            throw new org.springframework.security.access.AccessDeniedException("Customer mismatch");
        }
        subscriptionService.subscribe(
                customerId,
                request.endpoint(),
                request.keys().p256dh(),
                request.keys().auth()
        );
        return ResponseEntity.noContent().build();
    }
}
