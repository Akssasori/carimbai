package com.app.carimbai.repositories;

import com.app.carimbai.models.fidelity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByCustomerId(Long customerId);

    Optional<PushSubscription> findByCustomerIdAndEndpoint(Long customerId, String endpoint);

    @Transactional
    void deleteByEndpoint(String endpoint);
}
