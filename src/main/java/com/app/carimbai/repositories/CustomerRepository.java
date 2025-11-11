package com.app.carimbai.repositories;

import com.app.carimbai.models.fidelity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
