package com.app.carimbai.dtos.staff.admin;

/**
 * Configuracoes booleanas armazenadas como JSONB em core.locations.flags.
 * Front sempre envia os 3 campos juntos para evitar perda parcial.
 */
public record LocationFlags(
        Boolean requirePinOnRedeem,
        Boolean enableScanA,
        Boolean enableScanB
) {
}
