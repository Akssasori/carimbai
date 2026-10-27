package com.app.carimbai.dtos.staff.admin;
import jakarta.validation.constraints.NotNull;

/**
 * Configuracoes booleanas armazenadas como JSONB em core.locations.flags.
 * Front sempre envia os 3 campos juntos para evitar perda parcial.
 */
public record LocationFlags(
        @NotNull Boolean requirePinOnRedeem,
        @NotNull Boolean enableScanA,
        @NotNull Boolean enableScanB
) {
}
