package com.app.carimbai.dtos;

public record RedeemQrPayload(
        Long cardId,
        String nonce,
        Long exp,
        String sig
) {
}
