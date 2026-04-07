package com.app.carimbai.sse;

import com.app.carimbai.events.CardEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
@RequiredArgsConstructor
public class SseEmitterRegistry {

    private final ConcurrentHashMap<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitter register(Long cardId, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);

        emitters.computeIfAbsent(cardId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> {
            List<SseEmitter> list = emitters.get(cardId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    emitters.remove(cardId);
                }
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    public void send(Long cardId, CardEvent event) {
        List<SseEmitter> list = emitters.get(cardId);
        if (list == null || list.isEmpty()) return;

        String eventName = event.type().name().toLowerCase();

        for (SseEmitter emitter : list) {
            try {
                String json = objectMapper.writeValueAsString(event);
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(json));
            } catch (IOException e) {
                log.debug("Failed to send SSE to cardId={}, removing emitter", cardId);
                list.remove(emitter);
            }
        }
    }
}
