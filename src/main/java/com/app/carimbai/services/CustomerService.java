package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateCustomerRequest;
import com.app.carimbai.dtos.customer.CustomerLoginRequest;
import com.app.carimbai.enums.SocialProvider;
import com.app.carimbai.execption.EmailAlreadyLinkedException;
import com.app.carimbai.models.fidelity.Card;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.models.fidelity.Program;
import com.app.carimbai.repositories.CustomerRepository;
import com.app.carimbai.services.social.VerifiedSocialIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerService {


    private final CustomerRepository customerRepository;

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


        return customer;

    }

    @Transactional
    public Customer socialLoginOrRegister(SocialProvider provider, VerifiedSocialIdentity identity) {
        String prefixedProviderId = provider.name().toLowerCase() + ":" + identity.subject();

        // 1. Match exato por providerId
        var existing = customerRepository.findByProviderId(prefixedProviderId);
        if (existing.isPresent()) {
            Customer c = existing.get();
            if (identity.name() != null && !identity.name().isBlank()) {
                c.setName(identity.name());
            }
            return customerRepository.save(c);
        }

        // 2. Sem providerId - tentar vincular por email
        if (identity.email() != null && !identity.email().isBlank()) {
            var byEmail = customerRepository.findByEmail(identity.email());
            if (byEmail.isPresent()) {
                Customer c = byEmail.get();
                if (c.getProviderId() == null || c.getProviderId().isBlank()) {
                    // Vincular ao provedor social
                    c.setProviderId(prefixedProviderId);
                    if (identity.name() != null && !identity.name().isBlank()) {
                        c.setName(identity.name());
                    }
                    return customerRepository.save(c);
                } else {
                    // Email já vinculado a outro provedor
                    throw new EmailAlreadyLinkedException(identity.email());
                }
            }
        }

        // 3. Criar novo customer
        Customer newCustomer = new Customer();
        newCustomer.setProviderId(prefixedProviderId);
        newCustomer.setEmail(identity.email());
        newCustomer.setName(identity.name());
        return customerRepository.save(newCustomer);
    }

}
