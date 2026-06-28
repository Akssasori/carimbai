package com.app.carimbai.dtos.admin;

import com.app.carimbai.enums.StaffRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStaffUserRequest(

        @NotNull Long merchantId,
        @NotBlank @Email @Size(max = 160) String email,
        // Política mínima de senha (SEC-016). BCrypt trunca em 72 bytes.
        @NotBlank @Size(min = 10, max = 72) String password,
        @NotNull StaffRole role
) {
}
