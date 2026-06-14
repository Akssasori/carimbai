package com.app.carimbai.dtos.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SetPinRequest(
        // PIN numérico de 4 a 10 dígitos (SEC-017).
        @NotBlank @Pattern(regexp = "\\d{4,10}", message = "PIN deve ter de 4 a 10 dígitos") String pin
) {
}
