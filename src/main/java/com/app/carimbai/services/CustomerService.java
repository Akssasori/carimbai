package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateCustomerRequest;
import com.app.carimbai.dtos.customer.CustomerLoginRequest;
import com.app.carimbai.dtos.customer.CustomerLoginResponse;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.repositories.CustomerRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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

    @Transactional
    public Customer loginOrRegister(CustomerLoginRequest req) {

        // Estratégia simples de match:
        // 1) providerId (se informado)
        // 2) senão, email
        // 3) senão, phone
        Optional<Customer> existingOpt = Optional.empty();

        if (req.providerId() != null && !req.providerId().isBlank()) {
            existingOpt = customerRepository.findByProviderId(req.providerId());
        }
        if (existingOpt.isEmpty() && req.email() != null && !req.email().isBlank()) {
            existingOpt = customerRepository.findByEmail(req.email());
        }
        if (existingOpt.isEmpty() && req.phone() != null && !req.phone().isBlank()) {
            existingOpt = customerRepository.findByPhone(req.phone());
        }

        boolean created = false;
        Customer customer;

        if (existingOpt.isPresent()) {
            customer = existingOpt.get();
            // opcional: atualizar dados se vierem preenchidos
            if (req.name() != null && !req.name().isBlank()) {
                customer.setName(req.name());
            }
            if (req.email() != null && !req.email().isBlank()) {
                customer.setEmail(req.email());
            }
            if (req.phone() != null && !req.phone().isBlank()) {
                customer.setPhone(req.phone());
            }
            if (req.providerId() != null && !req.providerId().isBlank()) {
                customer.setProviderId(req.providerId());
            }
        } else {
            customer = new Customer();
            customer.setName(req.name());
            customer.setEmail(req.email());
            customer.setPhone(req.phone());
            customer.setProviderId(req.providerId());
            created = true;
        }

        return customerRepository.save(customer);

    }
}
