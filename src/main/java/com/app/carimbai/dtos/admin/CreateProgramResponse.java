package com.app.carimbai.dtos.admin;

import java.time.OffsetDateTime;

public record CreateProgramResponse(
        Long id,
        Long merchantId,
        String name,
        Integer ruleTotalStamps,
        String rewardName,
        Integer expirationDays,
        String description,
        Boolean active,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String category,
        String terms,
        String imageUrl,
        Integer sortOrder
) {
}
