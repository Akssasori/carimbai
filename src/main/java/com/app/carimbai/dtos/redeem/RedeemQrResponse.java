package com.app.carimbai.dtos.redeem;

import java.util.UUID;

public record RedeemQrResponse(
        String type,
        Long cardId,
        UUID nonce,
        long exp,
        String sig
) {
}
