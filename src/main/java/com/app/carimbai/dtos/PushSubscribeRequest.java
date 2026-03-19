package com.app.carimbai.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PushSubscribeRequest(
        @NotNull Long customerId,
        @NotBlank String endpoint,
        @NotNull @Valid Keys keys
) {
    public record Keys(
            @NotBlank String p256dh,
            @NotBlank String auth
    ) {}
}
