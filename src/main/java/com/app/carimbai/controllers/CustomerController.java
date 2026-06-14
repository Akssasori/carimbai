package com.app.carimbai.controllers;

import com.app.carimbai.dtos.admin.CreateCustomerRequest;
import com.app.carimbai.dtos.admin.CreateCustomerResponse;
import com.app.carimbai.dtos.customer.CustomerLoginRequest;
import com.app.carimbai.dtos.customer.CustomerLoginResponse;
import com.app.carimbai.dtos.customer.SocialLoginRequest;
import com.app.carimbai.facade.CustomerSession;
import com.app.carimbai.facade.UserRegistrationFacade;
import com.app.carimbai.mappers.CustomerMapper;
import com.app.carimbai.services.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
            description = "Creates a new customer. Restrito ao PLATFORM_ADMIN (FIX-02 Fase D).")
    @PostMapping
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    public ResponseEntity<CreateCustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(customerMapper
                .customerToCreateCustomerResponse(customerService
                        .createCustomer(request)));

    }

    @Operation( summary = "Customer Login or Register (staff-only — balcão)",
            description = "Cadastro/recuperação de cliente por dados informados pelo staff (balcão). "
                    + "NÃO é self-service do cliente — para isso use social-login. FIX-02 Fase D / SEC-001.")
    @PostMapping("/login-or-register")
    @PreAuthorize("hasAnyAuthority('CASHIER','ADMIN','PLATFORM_ADMIN')")
    public ResponseEntity<CustomerLoginResponse> loginOrRegister(@Valid @RequestBody CustomerLoginRequest request) {
        return ResponseEntity.ok(customerMapper.customerToCustomerLoginResponse(userRegistrationFacade.registerUser(request)));
    }

    @Operation(summary = "Customer Social Login",
            description = "Logs in or registers a customer via Google, Apple or Facebook token.")
    @PostMapping("/social-login")
    public ResponseEntity<CustomerLoginResponse> socialLogin(@Valid @RequestBody SocialLoginRequest request) {
        CustomerSession session = userRegistrationFacade.socialLogin(request);
        return ResponseEntity.ok(
                customerMapper.customerToCustomerLoginResponse(session.customer(), session.token()));
    }
}
