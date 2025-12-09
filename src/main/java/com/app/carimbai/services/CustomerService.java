package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateCustomerRequest;
import com.app.carimbai.dtos.customer.CustomerLoginRequest;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.models.fidelity.Program;
import com.app.carimbai.repositories.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerService {

//    public static final long DEFAULT_PROGRAM_ID = 1L;
    private final CustomerRepository customerRepository;
//    private final ProgramService programService;
//    private final CardService cardService;

    public Customer createCustomer(CreateCustomerRequest request) {
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

    @Transactional
    public Customer loginOrRegister(CustomerLoginRequest request) {

        // Estratégia simples de match:
        // 1) providerId (se informado)
        // 2) senão, email
        // 3) senão, phone
        Optional<Customer> existingOpt = Optional.empty();

        if (request.providerId() != null && !request.providerId().isBlank()) {
            existingOpt = customerRepository.findByProviderId(request.providerId());
        }
        if (existingOpt.isEmpty() && request.email() != null && !request.email().isBlank()) {
            existingOpt = customerRepository.findByEmail(request.email());
        }
        if (existingOpt.isEmpty() && request.phone() != null && !request.phone().isBlank()) {
            existingOpt = customerRepository.findByPhone(request.phone());
        }

        boolean created = false;
        Customer customer;

        if (existingOpt.isPresent()) {
            customer = existingOpt.get();
            // opcional: atualizar dados se vierem preenchidos
            if (request.name() != null && !request.name().isBlank()) {
                customer.setName(request.name());
            }
            if (request.email() != null && !request.email().isBlank()) {
                customer.setEmail(request.email());
            }
            if (request.phone() != null && !request.phone().isBlank()) {
                customer.setPhone(request.phone());
            }
            if (request.providerId() != null && !request.providerId().isBlank()) {
                customer.setProviderId(request.providerId());
            }
        } else {
            customer = new Customer();
            customer.setName(request.name());
            customer.setEmail(request.email());
            customer.setPhone(request.phone());
            customer.setProviderId(request.providerId());
            created = true;
        }

        customer = customerRepository.save(customer);

//        ensureDefaultCard(customer);

        return customer;

    }

//    private void ensureDefaultCard(Customer customer) {
//
//        Program program = programService.findById(DEFAULT_PROGRAM_ID);
//
//        if (program == null) {
//            // não tem programa cadastrado ainda, não faz nada
//            return;
//        }
//
//        // já existe cartão para esse programa + cliente?
//        boolean exists = cardService
//                .findByProgramIdAndCustomerId(program.getId(), customer.getId())
//                .isPresent();
//
//        if (exists) {
//            return;
//        }
//
//        // se não, cria um card zerado
//        Card card = new Card();
//        card.setProgram(program);
//        card.setCustomer(customer);
//        card.setStampsCount(0);
//        // status e createdAt já tem default na entidade
//        cardService.save(card);
//    }
}
