package com.app.carimbai.dtos.customer;

public record CustomerLoginResponse(
        Long customerId,
        String name,
        String email,
        String phone,
        String providerId
) {
}
