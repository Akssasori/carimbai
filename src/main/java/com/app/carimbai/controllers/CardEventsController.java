package com.app.carimbai.controllers;

import com.app.carimbai.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Slf4j
public class CardEventsController {

    private final SseEmitterRegistry registry;

    @Value("${carimbai.qr-ttl.seconds:90}")
    private Integer qrTtlSeconds;

    @GetMapping(value = "/{cardId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable Long cardId) {
        long timeoutMs = (qrTtlSeconds + 10) * 1000L;
        SseEmitter emitter = registry.register(cardId, timeoutMs);

        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"status\":\"ok\"}"));
        } catch (IOException e) {
            log.debug("Failed to send initial SSE event for cardId={}", cardId);
        }

        return emitter;
    }
}
