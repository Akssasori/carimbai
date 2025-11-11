package com.app.carimbai.dtos;

public record StampResponse(boolean ok, Long cardId, int stamps, int needed, boolean rewardIssued) {
}
