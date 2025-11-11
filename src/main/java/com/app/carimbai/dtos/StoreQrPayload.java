package com.app.carimbai.dtos;

import java.util.UUID;

public record StoreQrPayload(Long locationId, UUID nonce, long exp, String sig) {
}
