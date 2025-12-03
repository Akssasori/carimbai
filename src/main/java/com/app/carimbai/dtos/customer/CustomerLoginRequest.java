package com.app.carimbai.dtos.customer;

public record CustomerLoginRequest(
        String name,
        String email,
        String phone,
        String providerId
) {
}
