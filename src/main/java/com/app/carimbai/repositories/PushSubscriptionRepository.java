package com.app.carimbai.repositories;

import com.app.carimbai.models.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByCustomerIdAndEndpoint(Long customerId, String endpoint);

    List<PushSubscription> findByCustomerId(Long customerId);
}
