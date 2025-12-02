package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateCustomerRequest;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.repositories.CustomerRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public Customer createCustomer(@Valid CreateCustomerRequest request) {
        return customerRepository.save(Customer.builder()
                .email(request.email())
                .phone(request.phone())
                .providerId(request.providerId())
                .build());
    }

    public Customer findById(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with id: " + customerId));
    }
}
