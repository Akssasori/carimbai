package com.app.carimbai.repositories;

import com.app.carimbai.models.fidelity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByProviderId(String providerId);
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByPhone(String phone);
}
