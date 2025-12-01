package com.app.carimbai.controllers;

import com.app.carimbai.dtos.admin.CreateCustomerRequest;
import com.app.carimbai.dtos.admin.CreateCustomerResponse;
import com.app.carimbai.mappers.CustomerMapper;
import com.app.carimbai.services.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;

    @PostMapping("/customers")
    public ResponseEntity<CreateCustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(customerMapper
                .customerToCreateCustomerResponse(customerService
                        .createCustomer(request)));

    }
}
