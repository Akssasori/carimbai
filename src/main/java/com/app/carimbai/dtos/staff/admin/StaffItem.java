package com.app.carimbai.dtos.staff.admin;

import com.app.carimbai.enums.StaffRole;

import java.time.OffsetDateTime;

/**
 * Linha do listing de staff no merchant ativo.
 * - active / role refletem o vinculo em core.staff_user_merchants,
 *   nao o flag global do staff_user. Isso bate com o modelo
 *   N:N onde um staff pode ser ADMIN em um merchant e CASHIER em outro.
 */
public record StaffItem(
        Long staffId,
        String email,
        StaffRole role,
        Boolean active,
        Boolean isDefault,
        OffsetDateTime createdAt
) {
}
