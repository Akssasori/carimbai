package com.app.carimbai.dtos.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record EnrollmentProof(@NotNull UUID nonce, @Positive long exp,
                              @NotBlank @Size(max = 128) String sig) {}
