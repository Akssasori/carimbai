package com.app.carimbai.dtos;

public record CardItemDto(
    Long cardId,
    Long programId,
    String programName,
    String merchantName,
    String rewardName,
    Integer stampsCount,
    Integer stampsNeeded,
    String status,
    Boolean hasReward
) {
}
