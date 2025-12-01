package com.app.carimbai.dtos.admin;

public record CreateCustomerResponse(
        
        String email,
        String phone,
        String providerId
) {
}
