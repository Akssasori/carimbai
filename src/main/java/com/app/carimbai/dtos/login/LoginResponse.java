package com.app.carimbai.dtos.login;

public record LoginResponse(
        String token,
        Long staffId,
        Long merchantId,
        String role,
        String email
) {
}
