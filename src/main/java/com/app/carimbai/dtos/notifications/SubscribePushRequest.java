package com.app.carimbai.dtos.notifications;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Cliente envia (a) endpoint do push provider e (b) as duas chaves geradas
 * pelo proprio navegador. `customerId` no body e ignorado pelo backend
 * (vem do JWT) — mantido por compatibilidade com o front atual.
 */
public record SubscribePushRequest(
        Long customerId,
        @NotBlank String endpoint,
        @NotNull @Valid Keys keys
) {
    public record Keys(
            @NotBlank String p256dh,
            @NotBlank String auth
    ) {}
}
