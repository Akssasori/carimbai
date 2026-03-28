package com.app.carimbai.facade;

import com.app.carimbai.dtos.customer.CustomerLoginRequest;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.services.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserRegistrationFacade {

    private final CustomerService customerService;

    @Transactional
    public Customer registerUser(CustomerLoginRequest request) {
        return customerService.loginOrRegister(request);
    }
}
