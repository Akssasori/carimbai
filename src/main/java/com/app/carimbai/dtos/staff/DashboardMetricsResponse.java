package com.app.carimbai.dtos.staff;

import java.time.OffsetDateTime;

public record DashboardMetricsResponse(
        long stampsToday,
        long rewardsToday,
        long totalCustomers,
        OffsetDateTime generatedAt
) {
}
