package com.app.carimbai.events;

import com.app.carimbai.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class CardEventListener {

    private final SseEmitterRegistry sseEmitterRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCardEvent(CardEvent event) {
        sseEmitterRegistry.send(event.cardId(), event);
    }
}
