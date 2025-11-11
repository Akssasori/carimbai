package com.app.carimbai.dtos;

import java.util.UUID;

public record CustomerQrPayload(Long cardId, UUID nonce, long exp, String sig) {
}
