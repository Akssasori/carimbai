package com.app.carimbai.repositories;

import com.app.carimbai.models.fidelity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Customer c where c.id=:id")
    Optional<Customer> findForUpdate(Long id);

    Optional<Customer> findByProviderId(String providerId);
    Optional<Customer> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<Customer> findByPhone(String phone);
}
