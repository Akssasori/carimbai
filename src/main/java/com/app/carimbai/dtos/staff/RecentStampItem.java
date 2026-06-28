package com.app.carimbai.dtos.staff;

import java.time.OffsetDateTime;

public record RecentStampItem(
        Long id,
        Long cardId,
        Long customerId,
        String customerName,
        String programName,
        Integer stampsCount,
        Integer stampsNeeded,
        String cashierEmail,
        String locationName,
        OffsetDateTime whenAt
) {
}
