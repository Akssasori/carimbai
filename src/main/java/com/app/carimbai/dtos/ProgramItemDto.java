package com.app.carimbai.dtos;

import java.time.OffsetDateTime;

public record ProgramItemDto(
        Long id,
        String name,
        String description,
        Integer ruleTotalStamps,
        String rewardName,
        String category,
        String imageUrl,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        Integer sortOrder
) {
}
