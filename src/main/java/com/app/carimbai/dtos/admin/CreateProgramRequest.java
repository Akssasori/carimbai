package com.app.carimbai.dtos.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProgramRequest(

        @NotNull Long merchantId,
        @NotBlank String name,
        Integer ruleTotalStamps,
        String rewardName,
        Integer expirationDays
) {
}
