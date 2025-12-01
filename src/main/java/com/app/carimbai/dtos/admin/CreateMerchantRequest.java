package com.app.carimbai.dtos.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record CreateMerchantRequest(
        @NotBlank String name,
        String document
) {
}
