package com.app.carimbai.dtos.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record CustomerLoginRequest(
        @Size(max = 120) String name,
        @Email @Size(max = 160) String email,
        @Size(max = 30) String phone,
        @Size(max = 80) String providerId
) {
}
