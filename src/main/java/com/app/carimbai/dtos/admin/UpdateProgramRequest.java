package com.app.carimbai.dtos.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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
        // Bloqueia esquemas perigosos (javascript:/data:) — XSS no PWA (SEC-023). Vazio/nulo permitido.
        @Pattern(regexp = "^$|^https?://.+", message = "imageUrl deve começar com http(s):// ou ser vazio")
        String imageUrl,
        @PositiveOrZero Integer sortOrder
) {
}
