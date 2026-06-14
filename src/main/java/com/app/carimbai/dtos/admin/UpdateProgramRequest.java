package com.app.carimbai.dtos.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.OffsetDateTime;

public record UpdateProgramRequest(
        String name,
        @Min(1) Integer ruleTotalStamps,
        String rewardName,
        @Positive Integer expirationDays,
        String description,
        Boolean active,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String category,
        String terms,
        String imageUrl,
        @PositiveOrZero Integer sortOrder
) {
}
