package com.app.carimbai.dtos.customer;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CounterCustomerRequest(
        @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 160) String email,
        @Size(max = 30) String phone
) {
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported counter registration field");
    }
}
