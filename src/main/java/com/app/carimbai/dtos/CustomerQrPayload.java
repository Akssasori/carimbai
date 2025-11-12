package com.app.carimbai.dtos;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CustomerQrPayload(@NotNull Long cardId, @NotNull UUID nonce, @NotNull long exp, @NotNull String sig) {
}
