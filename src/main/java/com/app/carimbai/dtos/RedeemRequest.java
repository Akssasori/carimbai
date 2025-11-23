package com.app.carimbai.dtos;

import jakarta.validation.constraints.NotNull;

public record RedeemRequest(
        @NotNull Long cardId,
        Long locationId
) {
}
