package com.app.carimbai.dtos.admin;

public record AdminCardResponse(
        Long id,
        Long programId,
        Long customerId,
        Integer stampsCount,
        String status
) {
}
