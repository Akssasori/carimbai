package com.app.carimbai.dtos.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import com.app.carimbai.dtos.customer.EnrollmentProof;

public record CreateCardRequest(

        @NotNull @Positive Long programId,
        @NotNull @Positive Long customerId,
        @NotNull @Valid EnrollmentProof enrollmentProof
) {
}
