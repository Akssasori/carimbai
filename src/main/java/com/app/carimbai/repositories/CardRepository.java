package com.app.carimbai.repositories;

import com.app.carimbai.models.fidelity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    Optional<Card> findByProgramIdAndCustomerId(Long programId, Long customerId);

    @Query("SELECT c FROM Card c " +
           "JOIN FETCH c.program p " +
           "JOIN FETCH p.merchant m " +
           "WHERE c.customer.id = :customerId " +
           "ORDER BY c.createdAt DESC")
    List<Card> findByCustomerIdWithProgram(@Param("customerId") Long customerId);
}
