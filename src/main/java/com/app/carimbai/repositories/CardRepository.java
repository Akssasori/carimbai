package com.app.carimbai.repositories;

import com.app.carimbai.models.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {
    Optional<Card> findByProgramIdAndCustomerId(Long programId, Long customerId);
}
