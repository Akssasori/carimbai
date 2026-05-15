package com.app.carimbai.dtos.login;

public record RefreshTokenResponse(
        String token,
        String refreshToken
) {
}
