package com.app.carimbai.dtos;

import java.util.UUID;

public record QrTokenResponse(String type, Long idRef, UUID nonce, long exp, String sig) {
}
