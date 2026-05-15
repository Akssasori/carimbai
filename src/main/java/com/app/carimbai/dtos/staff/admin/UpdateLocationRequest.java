package com.app.carimbai.dtos.staff.admin;

/**
 * Patch parcial de location. Campos null = nao mexer.
 * IMPORTANTE: quando `flags` chega != null, sobrescreve o JSONB inteiro
 * com os 3 campos. Front deve sempre enviar os 3 booleans juntos.
 */
public record UpdateLocationRequest(
        String name,
        String address,
        Boolean active,
        LocationFlags flags
) {
}
