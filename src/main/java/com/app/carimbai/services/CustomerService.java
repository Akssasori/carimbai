package com.app.carimbai.services;

import com.app.carimbai.dtos.admin.CreateCustomerRequest;
import com.app.carimbai.dtos.customer.CounterCustomerRequest;
import com.app.carimbai.mappers.CustomerMapper;
import org.springframework.security.access.AccessDeniedException;
import com.app.carimbai.enums.SocialProvider;
import com.app.carimbai.execption.EmailAlreadyLinkedException;
import com.app.carimbai.models.fidelity.Customer;
import com.app.carimbai.repositories.CustomerRepository;
import com.app.carimbai.services.social.VerifiedSocialIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class CustomerService {


    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public Customer createCustomer(CreateCustomerRequest request) {
        if (request.providerId() != null && !request.providerId().isBlank()) {
            throw new AccessDeniedException("Social identities can only be assigned after provider verification");
        }
        return customerRepository.save(customerMapper.createRequestToCustomer(request));
    }

    public Customer findById(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with id: " + customerId));
    }

    @Transactional
    public Customer registerAtCounter(CounterCustomerRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw new AccessDeniedException("Existing customers must present their authenticated enrollment QR");
        }
        return customerRepository.save(customerMapper.counterRequestToCustomer(request));
    }

    @Transactional
    public Customer socialLoginOrRegister(SocialProvider provider, VerifiedSocialIdentity identity) {
        String prefixedProviderId = provider.name().toLowerCase() + ":" + identity.subject();

        // 1. Match exato por providerId
        var existing = customerRepository.findByProviderId(prefixedProviderId);
        if (existing.isPresent()) {
            Customer c = existing.get();
            if (!Boolean.TRUE.equals(c.getActive())) throw new com.app.carimbai.execption.InvalidCredentialsException();
            if (c.getProfileUpdatedAt()==null && identity.name() != null && !identity.name().isBlank()) {
                c.setName(identity.name());
            }
            return customerRepository.save(c);
        }

        // 2. Sem providerId - tentar vincular por email
        if (identity.email() != null && !identity.email().isBlank()) {
            var byEmail = customerRepository.findByEmail(identity.email());
            if (byEmail.isPresent()) {
                Customer c = byEmail.get();
                if (!Boolean.TRUE.equals(c.getActive())) throw new com.app.carimbai.execption.InvalidCredentialsException();
                if ((c.getProviderId() == null || c.getProviderId().isBlank()) && identity.emailVerified()) {
                    // Vincular ao provedor social
                    c.setProviderId(prefixedProviderId);
                    if (c.getProfileUpdatedAt()==null && identity.name() != null && !identity.name().isBlank()) {
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
