package com.app.carimbai.dtos.login;

public record MerchantInfo(
        Long merchantId,
        String merchantName,
        String role,
        boolean isDefault
) {
}
