package com.app.carimbai.dtos.customer;

import com.app.carimbai.enums.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SocialLoginRequest(
        @NotNull SocialProvider provider,
        @NotBlank String token
) {
}
