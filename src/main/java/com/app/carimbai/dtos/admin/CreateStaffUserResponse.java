package com.app.carimbai.dtos.admin;

import com.app.carimbai.enums.StaffRole;

public record CreateStaffUserResponse(
        Long id,
        String email,
        Long merchantId,
        StaffRole role
) {
}
