package com.app.carimbai.dtos.customer;

public record CustomerLoginResponse(
        Long customerId,
        String name,
        String email,
        String phone,
        // JWT de cliente — preenchido no social-login (FIX-02). null no login-light.
        String token
) {
}
