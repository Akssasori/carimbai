package com.app.carimbai.dtos;

public record RedeemResponse(
        boolean ok,
        Long rewardId,
        Long cardId,
        int stampsAfter
) {
}
