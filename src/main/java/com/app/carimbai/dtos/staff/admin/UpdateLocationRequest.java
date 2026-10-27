package com.app.carimbai.dtos.staff.admin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Patch parcial de location. Campos null = nao mexer.
 * IMPORTANTE: quando `flags` chega != null, sobrescreve o JSONB inteiro
 * com os 3 campos. Front deve sempre enviar os 3 booleans juntos.
 */
public record UpdateLocationRequest(
        @Size(min = 1, max = 120) String name,
        @Size(max = 255) String address,
        Boolean active,
        @Valid LocationFlags flags
) {
}
