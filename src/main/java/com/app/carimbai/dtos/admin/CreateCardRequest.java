package com.app.carimbai.dtos.admin;

import jakarta.validation.constraints.NotNull;

public record CreateCardRequest(

        @NotNull Long programId,
        @NotNull Long customerId
) {
}
