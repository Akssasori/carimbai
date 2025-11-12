package com.app.carimbai.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RedeemRequest(
        @NotNull Long cardId,
        @NotNull Long cashierId,
        @NotNull @Size(min = 4, max = 10) String cashierPin,
        // opcional: para auditoria/relatório (se souber a unidade no momento do resgate)
        Long locationId
) {
}
