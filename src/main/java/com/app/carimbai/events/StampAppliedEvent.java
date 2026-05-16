package com.app.carimbai.events;

/**
 * Disparado apos persistir um Stamp e a transacao commitar.
 * Consumido por listeners interessados em side-effects pos-carimbo
 * (hoje: notificacao push quando perto de fechar / cartao cheio).
 */
public record StampAppliedEvent(
        Long stampId,
        Long cardId,
        Long customerId,
        Long merchantId,
        String merchantName,
        String programName,
        int stampsCount,
        int stampsNeeded
) {
}
