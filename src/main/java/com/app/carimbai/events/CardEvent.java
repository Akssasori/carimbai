package com.app.carimbai.events;

public record CardEvent(Type type, Long cardId, int stampsCount, int stampsNeeded, String status) {

    public enum Type {
        STAMP_APPLIED,
        REDEEMED
    }

    public static CardEvent stampApplied(Long cardId, int stampsCount, int stampsNeeded, String status) {
        return new CardEvent(Type.STAMP_APPLIED, cardId, stampsCount, stampsNeeded, status);
    }

    public static CardEvent redeemed(Long cardId, int stampsCount, int stampsNeeded, String status) {
        return new CardEvent(Type.REDEEMED, cardId, stampsCount, stampsNeeded, status);
    }
}
