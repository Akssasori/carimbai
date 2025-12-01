package com.app.carimbai.dtos.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record CreateMerchantResponse(
        @NotBlank String name,
        String document
) {
}
