package com.app.carimbai.dtos.admin;

public record CreateCustomerRequest(

        String email,
        String phone,
        String providerId
) {
}
