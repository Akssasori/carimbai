package com.app.carimbai.dtos.staff.admin;

import com.app.carimbai.enums.StaffRole;

/**
 * Patch parcial do vinculo staff_user_merchants para o staffId/merchantId.
 * Campos null = nao mexer.
 */
public record UpdateStaffMerchantRequest(
        StaffRole role,
        Boolean active
) {
}
