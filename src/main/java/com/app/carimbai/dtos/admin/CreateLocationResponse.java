package com.app.carimbai.dtos.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateLocationResponse(

        @NotNull Long merchantId,
        @NotBlank String name,
        String address
) {
}
