package com.app.carimbai.dtos.login;

import java.util.List;

public record LoginResponse(
        String token,
        String refreshToken,
        Long staffId,
        Long merchantId,
        String role,
        String email,
        List<MerchantInfo> merchants
) {
}
