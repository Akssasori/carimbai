package com.app.carimbai.dtos.login;

import jakarta.validation.constraints.NotNull;

public record SwitchMerchantRequest(
        @NotNull Long merchantId
) {
}
