package com.app.carimbai.controllers;

import com.app.carimbai.dtos.admin.CreateCustomerRequest;
import com.app.carimbai.dtos.admin.CreateCustomerResponse;
import com.app.carimbai.dtos.customer.CustomerLoginRequest;
import com.app.carimbai.dtos.customer.CustomerLoginResponse;
import com.app.carimbai.facade.UserRegistrationFacade;
import com.app.carimbai.mappers.CustomerMapper;
import com.app.carimbai.services.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
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
    private final UserRegistrationFacade userRegistrationFacade;

    @Operation( summary = "Create a new customer",
            description = "Creates a new customer with the provided details.")
    @PostMapping
    public ResponseEntity<CreateCustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(customerMapper
                .customerToCreateCustomerResponse(customerService
                        .createCustomer(request)));

    }

    @Operation( summary = "Customer Login or Register",
            description = "Logs in an existing customer or registers a new one based on the provided details.")
    @PostMapping("/login-or-register")
    public ResponseEntity<CustomerLoginResponse> loginOrRegister(@RequestBody CustomerLoginRequest request) {
        return ResponseEntity.ok(customerMapper.customerToCustomerLoginResponse(userRegistrationFacade.registerUser(request), true));
    }
}
