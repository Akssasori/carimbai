package com.app.carimbai.dtos.staff;

import java.time.OffsetDateTime;

public record RecentRewardItem(
        Long id,
        Long cardId,
        Long customerId,
        String customerName,
        String programName,
        String rewardName,
        String cashierEmail,
        String locationName,
        OffsetDateTime issuedAt
) {
}
