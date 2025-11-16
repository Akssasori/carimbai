package com.app.carimbai.dtos;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StoreQrPayload(
        @NotNull Long cardId,
        @NotNull Long locationId,
        @NotNull UUID nonce,
        @NotNull Long exp,
        @NotNull String sig
) {
}
