package com.app.carimbai.dtos.admin;

import com.app.carimbai.enums.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStaffUserRequest(

        @NotNull Long merchantId,
        @NotBlank String email,
        @NotBlank String password,
        @NotNull StaffRole role
) {
}
