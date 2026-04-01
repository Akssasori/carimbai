package com.app.carimbai.dtos.admin;

import java.time.OffsetDateTime;

public record UpdateProgramRequest(
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
