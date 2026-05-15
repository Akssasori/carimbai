package com.app.carimbai.dtos.staff.admin;

import java.time.OffsetDateTime;

/**
 * Visao admin de um program: inclui campos que o endpoint publico (`ProgramItemDto`)
 * omite, como `active` e `terms`. Usado em GET /api/merchants/{mId}/admin/programs.
 */
public record AdminProgramItem(
        Long id,
        Long merchantId,
        String name,
        String description,
        Integer ruleTotalStamps,
        String rewardName,
        Integer expirationDays,
        Boolean active,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String category,
        String terms,
        String imageUrl,
        Integer sortOrder
) {
}
