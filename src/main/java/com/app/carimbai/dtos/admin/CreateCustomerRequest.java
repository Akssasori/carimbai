package com.app.carimbai.dtos.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(

        @Email @Size(max = 160) String email,
        @Size(max = 30) String phone,
        @Size(max = 80) String providerId
) {
}
