package com.app.carimbai.facade;

import com.app.carimbai.dtos.customer.CustomerLoginRequest;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.services.CardService;
import com.app.carimbai.services.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserRegistrationFacade {

    private final CustomerService customerService;
    private final CardService cardService;

    @Transactional
    public Customer registerUser(CustomerLoginRequest request) {
        // 1. Chama serviço de cliente para salvar/buscar
        Customer customer = customerService.loginOrRegister(request);

        // 2. Chama serviço de cartão para garantir o cartão default
        cardService.ensureDefaultCard(customer);

        return customer;
    }
}
